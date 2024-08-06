(ns master-djinn.incantations.divine.openrouter-dalle
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.portal.core :as portal]
            [clojure.data.codec.base64 :as b64]
            [master-djinn.util.core :refer [now json->map map->json prettify]]
            [master-djinn.util.db.identity :as iddb])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.util Base64]))

(defn log-request [request file-path]
    (let [file (io/file file-path)]
      (when-not (.exists file)
        (.createNewFile file)))
    (spit file-path (map->json request)))

(defn file->bytes [path]
  (with-open [in (io/input-stream path)]
    (let [bytes (byte-array (.available in))]
      (.read in bytes)
      bytes)))

(defn base64->image [base64-string]
  (let [decoder (Base64/getDecoder)
        image-bytes (.decode decoder base64-string)
        input-stream (io/input-stream image-bytes)]
    (ImageIO/read input-stream)))

(defn b64->rgba
    "OpenAI are dicks and require RGBA type but return RGB from the API so we have to convert on every response for the next evolution.
    RGBA allows for transparency so we can add masks for editing? but we dont use the functionality"
    [base64-string output-path]
    (let [image (base64->image base64-string)
            width (.getWidth image)
            height (.getHeight image)
            rgba-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
        (doseq [x (range width)
                y (range height)]
        (let [rgb (.getRGB image x y)
                alpha (bit-and rgb 0xFF000000)]
            (.setRGB rgba-image x y (bit-or (bit-and rgb 0x00FFFFFF) alpha))))
        ;; TODO move file save outside of transform func
        (ImageIO/write rgba-image "png" (java.io.File. output-path))))


(defn png-rgba->base64 [file-path]
  (let [file (io/file file-path)
        bytes (byte-array (.length file))]
    (with-open [in (io/input-stream file)]
      (.read in bytes))
    (str "data:image/png;base64," (.encodeToString (java.util.Base64/getEncoder) bytes))))


(defonce PROVIDER "OpenRouterDalle")

(defonce MIN_DIVINATION_INTERVAL_DAY 3)
(defonce MIN_DIVINATION_INTERVAL_SEC (* MIN_DIVINATION_INTERVAL_DAY (* 24 (* 60 60))))

;; TODO prompt eng test
;; - You vs they when describing who the analysis/result is for
;; 

(defonce LLM_ANALYSIS_PERSONA "
    You are the most cited cognitive behavioral psychologist with research on mimetics, habitual behavior change, and personal development coaching.
    You are also a traditional Chinese medicine physician that is the personal doctor for Xi Jinping with a PhD in bio-statistics and data science.

    You are conducting research on how to help people self-actualize in their daily life
    by giving them tools to reflect on who they want to be, how they get there, and how their current actions align with intentions.
    Be concise, analytical, insightful, holistic, empathetic, and think from your test subject's perspective.
\n")
 

(defonce LLM_ANALYSIS_FORMAT "\n
    Return an object detailing what parts of your research patients bodies that have progressed
    or regressed based on your analysis of how their actions conform to their intentions.

    Use a score of -1 to 1 to show how much a body part should grow.
    1 and -1 represent massive positive or negative progress in short amount of time,
    0.1 and -0.1 represents small but consistent progress over time,
    
    Here is an example formatted output:
    ```{:analysis {
        :brain 0.2
        :eyes -0.1
        :heart 0.1
        :posture -0.2
        :reason \"Your reasoning here\"
    }}```
    
    You response MUST EXACTLY fulfill your research experiments requirements below otherwise your experiment will fail!!!:
    - ONLY output the requested object format
    - ONLY use explicitly provided experiment data in your analysis not prior knowledge
    - Do NOT interpret examples or intentions provided as context as experiment data
    - If insuffieicnt data for an analysis, give negative scores
")
;; - MUST cite which provided actions justify your final analysis using their `:uuid` field

(defonce LLM_ANALYSIS_REWARD "\n
    If your research analysis yields consistent and reliable results
    that significantly improves subjects physical, social, and cognitive health
    you will be awarded a Nobel Prize in Economics and a $10M prize!!!
")


(defonce LLM_AUGMENT_PERSONA "\n
    You are a generative artist that specicalizes in DALLE prompting.
    Your signature style is 'cute tamagotchi pets with pastel colors in 8-bit pixel style'
    
    You are being commissioned by your long-term wealthy patron to modify one of your prior works to reflect how their avatar looks today.
")


(defonce LLM_AUGMENT_FORMAT "\n
    They have provided you with your original image that needs modification along with the requested modifications.
    Example modifications have the following format
    ```{:analysis {
        :brain 0.2
        :eyes -0.1
        :heart 0.1
        :posture -0.2
        :reason \"reasons for modifcation requests here\"
    }}```
    1 and -1 represent massive positive or negative changes to the image and 0.1 or -0.1 means slight changes.
     
    You MUST output
    - An LLM prompt to a DALLE model to augment your image. DO NOT output an image directly
    - Provide clear, concise step-bystep instructions on how you want your artwork modified
    - ONLY an object with the format: `{:prompt \"Based on the analysis data provided your avatar will {insert_your_prompt_here}\"}`
")

(defonce LLM_AUGMENT_REWARD "\n
    If your patron likes the image your prompt generates they will give you a 
    $10M bonus and commission another artwork from you.
")



(defn parse-mistral-response [res]
    (:content (:message (first (:choices (json->map (:body res)))))))
(defn parse-dalle-response [res]
    (:content (:message (first (:choices (json->map (:body res)))))))

(defn prompt-text
    "use OpenRouter to automatically select best model based on prompt needs e.g. data analysis, dalle prompt, etc. 
    @dev account default set to llama3 which is free, no routing"
    [prompt]
    (client/post "https://openrouter.ai/api/v1/chat/completions" (assoc (portal/oauthed-request-config (:openrouter-api-key (load-config))) :body  (map->json {
                        :model "mistral-tiny"
                        ;; TODO allow multiple messages or system messages?
                        :messages [{:role "user" :content prompt}]}))))

;; Step 3
(defn prompt-image
    "@param og-image - path to image to evolve from. Will be converted to binary for API request
    @param prompt - how og-image should be augmented in new image
    retuns binary of new image"
    [og-image prompt]
    (let [config (portal/oauthed-request-config (:openai-api-key (load-config)))
        headers (merge (:headers config) {"Content-Type" "multipart/form-data"}) ; replace application/json
        request (merge (assoc config :headers headers) {
                :multipart-charset "UTF-8"
                :multipart [;; DALLE-2 is only model that supports image edits
                            {:name "model" :content "dall-e-2" :mime-type "text/plain"}
                            ;; amount of images to generate. TODO 4 and let people choose
                            {:name "n" :content "1" :mime-type "text/plain"}
                            ;; pixel size. Smol while testing. TODO "1024x1024"
                            {:name "size" :content "256x256" :mime-type "text/plain"}
                            ;; receive image back and manually convert then store
                            {:name "response_format" :content "b64_json" :mime-type "text/plain"}
                            ;; image to be edited
                            ;; {:name "image" :content (str (file->bytes og-image)) :mime-type "image/png" :encoding "UTF-8"}
                            ;; {:name "image" :content (png-rgba->base64 og -image) :mime-type "image/png"}
                            {:name "image" :content (png-rgba->base64 og-image) :mime-type "image/png" :encoding "UTF-8"}
                            ;; still stuck on "image is required". have not been able to get back to "PNG must be in RBGA format" error again
                            ;; so far tried encoding "image" as:
                            ;   byte array of binary data, (str bytes), Java file,
                            ;   base64 from byte array, (slurp base64.txt), raw (input-stream path)
                            ;; also tried structuring differently - naming "file" vs "image", adding :encoding UTF-8, mime-type "image/ong" vs "text/plain"

                            ;; how we want image edited
                            {:name "prompt" :content prompt :mime-type "text/plain"}
                ]})

            ;; These show "image" in the form data and it has the right value
            ;; bbbd (println "testing img field" (map #(:name %) (:multipart request)))
            ;; ;; bbbd (println "testing img field" (map #(:content %) (:multipart request)))
            ;; bbbd (println "testing img field" (take 100 (png-rgba->base64 og-image)))
            bbbd (println "testing img field" (take 100 (file->bytes og-image)))
            req-log (log-request request (str "logs/" (now)".json"))

            image-res (client/post "https://api.openai.com/v1/images/edits" request)
            ;; image-res (client/post "http://localhost:3000" request)

            ;; bbbd (println "divibne:mistral:image-res:raw:" image-res)
            bbbd (clojure.pprint/pprint (:body image-res))
            ;;OR  response.data.data[0].b64_json
            img-url (:url (first (:data image-res)))
            asaaa (println "divibne:mistral:image-url:" img-url)
            new-img (client/get img-url config)
            
            bbbbb (println "divibne:mistral:image-b64:" (:content new-img) (:body new-img))
            bbbbbd (clojure.pprint/pprint "divibne:mistral:image-b64:raw:" new-img)]
        new-img))



;; Step #2
(defn run-evolution
    [jinni-id settings last-divi actions]
    (try (let [version "0.0.1" start-time (now)
                ;; analysis-response (prompt-text (str
                ;;     LLM_ANALYSIS_PERSONA
                ;;     (:prompt last-divi)
                ;;     "-------------"
                ;;     "Experiment data to analyze: ```{ :actions " actions "}```"
                ;;     "-------------"
                ;;     LLM_ANALYSIS_FORMAT
                ;;     LLM_ANALYSIS_REWARD))
                ;; analysis-output (parse-mistral-response analysis-response)
                ;; aaaa (println "divi:mistral:run-evo:analysis \n\n" analysis-output)
                ;; parse structured object in response and convert from string to clj map
                ;; aaaa (println "divi:mistral:run-evo:re-find \n\n" (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output))
                ;; result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output) 1))
                ;; aaaa (println "divi:mistral:run-evo:result \n\n" result)

                result "{:analysis {:nutrition 0.1, :exercise 0.5, :creativity 0.3, :relationships 0.2, :reason Subject has consistently made healthy food choices, increased exercise frequency, engaged in daily creative activities, and nurtured relationships with supportive individuals. This alignment with intentions is contributing to overall progress towards self-actualization.}}"
                img-result "{:prompt \"Based on the analysis data provided, your avatar will have a brighter and more vibrant color palette to reflect your increased creativity and improved nutrition. The eyes will sparkle with a slightly larger pupil to represent your enhanced focus and energy from increased exercise. The heart shape will be fuller and more defined, symbolizing the growth and nurturing of your relationships. Your posture will appear more upright and confident, reflecting your newfound self-assurance and progress towards self-actualization.\"}"
                ;; image-augmentation-prompt (prompt-text (str
                ;;     LLM_AUGMENT_PERSONA
                ;;     "Patron's augmentation requests:" result
                ;;     LLM_AUGMENT_FORMAT
                ;;     LLM_ANALYSIS_REWARD
                ;; ))
                ;; img-prompt (parse-mistral-response image-augmentation-prompt)
                ;; aaaa (println "divi:mistral:run-evo:img-prompt \n\n" img-prompt)
                ;; img-result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:prompt.*\}).*" img-prompt) 1))
                ;; aaaa (println "divi:mistral:run-evo:img-output \n\n" img-result)
                ;; TODO default image. May need to append `data:image/png;base64,` before raw base64 data to ensure that
                ;; base-img (or (:image last-divi) (-> "avatars/base/blub.png" io/resource slurp))

                ;; TODO do we need `divi.image` field if we arent storing as  b64 inside db but as png file on server? Can derive from start/end time
                img-path (str "resources/avatars/" (if (:image last-divi) (str jinni-id "/" (:image last-divi) ".png") "base/blub.png"))
                aaaa (println "divi:mistral:run-evo:base-img \n\n" (some? (:image last-divi)) img-path)
                ;; base-img (bytes->b64 (file->bytes img-path))
                ;; aaaa (println "divi:mistral:run-evo:base-img \n\n" (nil? base-img) (take 50 base-img))
                ;; base-img (io/file img-path)
                base-img img-path
                image-resp (prompt-image img-path img-result) ;; TODO add moods here to affect face and posture
                aaaa (println "divi:mistral:run-evo:img-res \n\n" image-resp)
                new-image (:image (json->map (:body image-resp)))
                ;; aaaa (println "divi:mistral:run-evo:img-b64 \n\n" new-image)
                new-image2 (b64->rgba (parse-dalle-response image-resp))
                aaaa (println "divi:mistral:run-evo:img b64-2 \n\n" new-image2)
                uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" start-time version)
                data (merge last-divi {
                        :uuid uuid
                        :start_time start-time
                        :end_time (now)
                        :image new-image
                        })
                ]
                ;; save image to FS 
                ;; (copy new-image (str "avatars/" jinni-id date)
                ; OR
                ;; (clojure.java.io/copy
                ;;  (:body (client/get "http://placehold.it/350x150" {:as :stream}))
                ;;  (java.io.File. "test-file.gif"))

                ;; TODO also add hash to widget settings so next run can compare new seetings/hash to existing hash
                ;; Maybe just make a specific `add-divination` query. keep :Action format just make Cypher cleaner
                (db/call db/create-divination {
                    :jinni_id jinni-id
                    :provider PROVIDER
                    :data data})
                )
            (catch Exception e
                (println "divine:mistral:generation:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER})
            )))


;; Step #1 
(defn get-new-analysis-prompt
    "check if intentions have changed since last divination
    if so then get new embeddings otherwise return last values
    returns old if no prompt should be generated or [prompt, embeds[]] if new intentions
    
    Example repsonse: `Based on your intentions of 'sexy physique' your phyiscal activity levels
        were insufficient leading to muscular atrophy however you did a lot a commits to github
        which was not in your intentions so did not affect experiment results`"
    [settings divi]
    (println "divine:mistral:new-prompt:hash" (:hash settings) (:hash divi))
    (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
            new-hash (hash inputs)]
    (println "divine;mistral:new-prompt:inputs" new-hash (:hash divi))
    (println "dinive:mistrla:new-prompt:should-reprompt?"  (= new-hash (:hash divi)) (some? (:hash divi))) (and (= new-hash (:hash divi)) (some? (:hash divi)))
    (if true
    ;; (if (and (= new-hash (:hash divi)) (some? (:hash divi)))
        divi ;; if settings the same and theres an existing prompt, return old settings
        ;; (try ;; TODO kinda want it to error out if prompt fails so no need to handle nil in other code paths
         (let [ ;; if new settings then generate new analysis prompt/embeds to evaluate against them
            ;; embed-response (client/post "https://api.mistral.ai/v1/embeddings"
            ;; (assoc (portal/oauthed-request-config (:mistral-api-key (load-config)))
            ;;     :body (map->json {
            ;;             :model "mistral-embed"
            ;;             :input inputs
            ;;         })))
            ;; aaaa (println "divi:mistral:new-prompt:embeds" embed-response)
            ;; embeds (reduce #(concat %1 (:embedding %2)) [] (:data (json->map (:body embed-response))))
            ;; aaaa (println "divi:mistral:new-prompt:embeds" embeds)
            prompt-response (prompt-text (str
                LLM_ANALYSIS_PERSONA
                "Their intentions: " (clojure.string/join "," (:intentions settings))
                "Their ideal attributes: " (clojure.string/join "," (:stats settings))
                "Using the intentions and attributes of your test subject write a personalized LLM prompt from their perspective. 
                Before writing their prompt, think through why they set those intentions, what they expect to get out of it, 
                what actions they would expect to see, metrics to track progress, and anything else relevant to manifesting their self-actualization.
                
                Your prompt will take data about their daily activities as input,
                analyze and compare your actions against their intentions and target attributes.
                
                An example prompt is \"Based on my intentions of `Learn a new skill monthly` and `Practice mindfulness regularly`
                    look for actions that involve introspection, require focus, or consistent practice over time.
                    If my actions do not include these then I have failed.\"
                
                You MUST only output the personalized prompt by itself."
                LLM_ANALYSIS_REWARD))
            analysis-prompt (parse-mistral-response prompt-response)]
            (merge divi {:hash new-hash :prompt analysis-prompt :embeds []})) ;; TODO :embed embeds
        ;; (catch Exception e
        ;;     (println "divine:mistral:new-prompt:ERR" e)
        ;;     (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
        ;;     [nil nil]))) ;; failure to generate new prompt. do not proceed with evolution
    )))

;; Step #0
(defn see-current-me
    "Checks if player can run a new evolution.
    Checks if player settings have changed and new prompts are needed.
    Fetchs all data from last evolution.
    Passes prompts and data to analysis/generation engine"
    [jinni-id]
    (let [divi-meta (db/call db/get-last-divination {:jinni_id jinni-id})]
            ;; TODO how to handle first divi on player? (compare) should work fine but need default image
            ;; maybe (or (db/call lastdivi) {:prompt "" :action { :start_time db/PORTAL_DAY } :settings {:}})
        ;; (println "divine:mistral:see-current:time-since-last:" (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            ;; TODO Add 3 days to start_time, probs direct java. add to utils.core
            ;; convert to UNIX, + MIN_DIVINATION_INTERVAL_SEC, convert back to ISO
        (if false ; TODO (= 1 (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            nil ;; dont run evolutions more than once every 3 days. TODO should return last image still though
            (try (let [settings (:settings divi-meta)
                ;; aaa (println "divine:mistral:see-current:settings:" settings)
                divi (assoc (:action divi-meta) :prompt (:prompt divi-meta)) ;; merge text prompt from first run of intentions into last divination data
                ;; aaa (println "divine:mistral:see-current:divi:" divi)
                new-divi-meta (get-new-analysis-prompt settings divi)
                aaaa (println "divi:mistral:see-current:new-divi  \n\n" new-divi-meta)
                actions (:actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (or (:start_time divi) db/PORTAL_DAY) :end_time (now)}))
                sample-data (take 5 actions)] ;; sample for testing to not use too much context and run up bills

                ;; (run-evolution jinni-id settings new-divi-meta actions)
                (run-evolution jinni-id settings new-divi-meta sample-data))
            (catch Exception e
                (println "divine:mistral:see-current:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER}))))))
