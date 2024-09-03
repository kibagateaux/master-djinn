(ns master-djinn.incantations.divine.openrouter-dalle
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.portal.core :as portal]
            [wkok.openai-clojure.api :as oai]
            [clojure.data.codec.base64 :as b64]
            [master-djinn.util.core :refer [now json->map map->json prettify]]
            [master-djinn.util.db.identity :as iddb])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.util Base64]))

;; (defn log-request [request file-path]
;;     (let [file (io/file file-path)]
;;       (when-not (.exists file)
;;         (.createNewFile file)))
;;     (spit file-path (map->json request)))

;; (defn file->bytes [path]
;;   (with-open [in (io/input-stream path)]
;;     (let [bytes (byte-array (.available in))]
;;       (.read in bytes)
;;       bytes)))

;; (defn base64->image [base64-string]
;;   (let [decoder (Base64/getDecoder)
;;         image-bytes (.decode decoder base64-string)
;;         input-stream (io/input-stream image-bytes)]
;;     (ImageIO/read input-stream)))

;; (defn b64->rgba
;;     "OpenAI are dicks and require RGBA type but return RGB from the API so we have to convert on every response for the next evolution.
;;     RGBA allows for transparency so we can add masks for editing? but we dont use the functionality"
;;     [base64-string output-path]
;;     (let [image (base64->image base64-string)
;;             width (.getWidth image)
;;             height (.getHeight image)
;;             rgba-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
;;         (doseq [x (range width)
;;                 y (range height)]
;;         (let [rgb (.getRGB image x y)
;;                 alpha (bit-and rgb 0xFF000000)]
;;             (.setRGB rgba-image x y (bit-or (bit-and rgb 0x00FFFFFF) alpha))))
;;         ;; TODO move file save outside of transform func
;;         (ImageIO/write rgba-image "png" (java.io.File. output-path))))


;; (defn png-rgba->base64 [file-path]
;;   (let [file (io/file file-path)
;;         bytes (byte-array (.length file))]
;;     (with-open [in (io/input-stream file)]
;;       (.read in bytes))
;;     (str "data:image/png;base64," (.encodeToString (java.util.Base64/getEncoder) bytes))))


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
    (client/post "https://openrouter.ai/api/v1/chat/completions"
                (assoc (portal/oauthed-request-config (:openrouter-api-key (load-config)))
                        ;; TODO allow multiple messages or use system messages for persona/reward?
                        :body  (map->json { :messages [{:role "user" :content prompt}]}))))

;; Step 3
(defn prompt-image
    "@param og-image - path to image to evolve from. Will be converted to binary for API request
    @param prompt - how og-image should be augmented in new image
    @dev https://cljdoc.org/d/net.clojars.wkok/openai-clojure/0.18.1/doc/usage-openai

    retuns binary of new png image"
    [og-image prompt]
    (let [image-res (oai/create-image-edit {
                :image (io/file og-image) ;; TODO need rgb->rgba  func for sending or does library handle that?
                :prompt prompt
                :n 1
                :size "1024x1024"}
                {:api-key (:openai-api-key (load-config))
                :organization "jinni-health"})
            img-url (:url (first (:data image-res)))
            asaaa (println "divibne:mistral:image-url:" img-url)
            new-img (client/get img-url)] ;; no auth required so no config in request
        new-img))

(defn get-last-divi-img
    "checks if jinni already has already been divined
    if so returns last generated image
    if not returns base model to start divination"
    [jinni-id]
  (let [jinni-dir (str "resources/avatars/" jinni-id)
        aaa (println "jinni dir - " jinni-dir)
        iso-formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")
        files (->> (file-seq (io/file jinni-dir))
                    (filter #(.isFile %))
                    (map #(.getName %))
                    (map #(iso-formatter %))
                    (filter #(re-matches #"\d{4}-\d{2}-\d{2}.png" %)) ; TODO check that im actually saving in this file format
                    (sort-by #(java.time.LocalDateTime/parse (clojure.string/replace % #"\.png" "")))) ; this should work but getting ISO timestamp not short date
        ;; TODO check that this actually sorts chronologically
        d-img (last files)]
    (if (nil? d-img) ; if no past divinations return base model else lastest chronological divination
        "resources/avatars/base/blub.png"
        (str jinni-dir "/" (last files) ".png"))))

;; (defn rgb->rgba
;;     [png-data]
;;     (let [input-stream (ByteArrayInputStream. png-data)
;;             rgb-image (ImageIO/read input-stream)
;;             width (.getWidth rgb-image)
;;             height (.getHeight rgb-image)
;;             rgba-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
        
;;         ;; Iterate through each pixel
;;         (dotimes [y height]
;;         (dotimes [x width]
;;             (let [rgb-pixel (.getRGB rgb-image x y)
;;                 r (bit-and (bit-shift-right rgb-pixel 16) 0xFF)
;;                 g (bit-and (bit-shift-right rgb-pixel 8) 0xFF)
;;                 b (bit-and rgb-pixel 0xFF)
;;                 rgba-pixel (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b 0xFF000000)] ;; Set alpha to 255
;;             (.setRGB rgba-image x y rgba-pixel))))
        
;;         ;; Convert RGBA image back to byte array
;;         (let [output-stream (ByteArrayOutputStream.)]
;;         (ImageIO/write rgba-image "png" output-stream)
;;         (.toByteArray output-stream))))


(defn rgb->rgba
    "OpenAI are dicks and require RGBA type but return RGB from the API so we have to convert on every response for the next evolution.
    RGBA allows for transparency so we can add masks for editing? but we dont use the functionality"
    [image]
    (let [width (.getWidth image)
            height (.getHeight image)
            rgba-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
        (doseq [x (range width)
                y (range height)]
        (let [rgb (.getRGB image x y)
                alpha (bit-and rgb 0xFF000000)]
            (.setRGB rgba-image x y (bit-or (bit-and rgb 0x00FFFFFF) alpha))))
        ;; TODO move file save outside of transform func
        ;; (ImageIO/write rgba-image "png" (java.io.File. output-path))
        rgba-image))

(defn ensure-dir [dir-path]
  (let [dir (io/file dir-path)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn save-divi-img
    "
    takes binary rgb png file from DALLE API
    converts from RGB to RGBA for later use (30% filesize increase, TODO inflight to DALLE API)
    saves image to resources/avatars/{jinni-id}/{YYYY-MM-DD}.png
    returns file path where image was saved
    "
    [jid image now]
    (let [dir (str "resources/avatars/" jid)
            day  (-> (java.time.LocalDateTime/parse now)
                    (.toLocalDate)
                    (.toString))
            
        file-path (str dir "/" day ".png")
        asdfasf (println "jinni dir - " dir file-path)]
        (ensure-dir dir)
        (with-open [stream (io/output-stream file-path)]
            (.write stream (rgb->rgba image)))))

;; Step #2
(defn run-evolution
    [jinni-id settings last-divi actions]
    (try (let [version "0.0.1" start-time (now)
                analysis-response (prompt-text (str
                    LLM_ANALYSIS_PERSONA
                    (:prompt settings)
                    "-------------"
                    "Experiment data to analyze: ```{ :actions " actions "}```"
                    "-------------"
                    LLM_ANALYSIS_FORMAT
                    LLM_ANALYSIS_REWARD))
                analysis-output (parse-mistral-response analysis-response)
                ;; aaaa (println "divi:mistral:run-evo:analysis \n\n" analysis-output)
                ;; parse structured object in response and convert from string to clj map
                aaaa (println "divi:mistral:run-evo:re-find \n\n" (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output))
                result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output) 1))
                ;; aaaa (println "divi:mistral:run-evo:result \n\n" result)

                image-augmentation-prompt (prompt-text (str
                    LLM_AUGMENT_PERSONA
                    "Patron's augmentation requests:" result ; TODO dont like raw str in prompt here. templatize for better experimentation/anayltics
                    LLM_AUGMENT_FORMAT
                    LLM_ANALYSIS_REWARD
                ))
                img-prompt (parse-mistral-response image-augmentation-prompt)
                augment-prompt (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:prompt.*\}).*" img-prompt) 1))

                ;; result "{:analysis {:nutrition 0.1, :exercise 0.5, :creativity 0.3, :relationships 0.2, :reason Subject has consistently made healthy food choices, increased exercise frequency, engaged in daily creative activities, and nurtured relationships with supportive individuals. This alignment with intentions is contributing to overall progress towards self-actualization.}}"
                ;; augment-prompt "{:prompt \"Based on the analysis data provided, your avatar will have a brighter and more vibrant color palette to reflect your increased creativity and improved nutrition. The eyes will sparkle with a slightly larger pupil to represent your enhanced focus and energy from increased exercise. The heart shape will be fuller and more defined, symbolizing the growth and nurturing of your relationships. Your posture will appear more upright and confident, reflecting your newfound self-assurance and progress towards self-actualization.\"}"
                
                ;; aaaa (println "divi:mistral:run-evo:img-prompt \n\n" img-prompt)
                ;; aaaa (println "divi:mistral:run-evo:img-output \n\n" augment-prompt)
                ;; TODO default image. May need to append `data:image/png;base64,` before raw base64 data to ensure that
                ;; base-img (or (:image last-divi) (-> "avatars/base/blub.png" io/resource slurp))

                ;; TODO do we need `divi.image` field if we arent storing as  b64 inside db but as png file on server? Can derive from start/end time
                ;; :image should be full path
                img-path (str "resources/avatars/" (if (:image last-divi) (str jinni-id "/" (:image last-divi) ".png") "base/blub.png"))
                real-img-path (get-last-divi-img jinni-id)
                sanfuai (println "divi:mistral:run-evo:base-img \n\n" img-path)
                ;; base-img (bytes->b64 (file->bytes img-path))
                ;; aaaa (println "divi:mistral:run-evo:base-img \n\n" (nil? base-img) (take 50 base-img))
                ;; base-img (io/file img-path)
                new-image (prompt-image img-path augment-prompt) ;; TODO add moods here to affect face and posture
                ;; aaaa (println "divi:mistral:run-evo:img-res \n\n" image-resp)
                ;; new-image (:image (json->map (:body image-resp)))
                ;; aaaa (println "divi:mistral:run-evo:img-b64 \n\n" new-image)
                ;; new-image2 (b64->rgba (parse-dalle-response image-resp))
                
                ;; TODO save image-resp to file system (save-divi-img jinni-id image-resp (now))
                ;; aaaa (println "divi:mistral:run-evo:img b64-2 \n\n" new-image)
                uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" start-time version)
                data (merge last-divi {
                        :uuid uuid
                        :start_time (:end_time last-divi) ;; TODO start_time should be end_time of last divi not start of this func call
                        :end_time (now)})
                ]
                sanfuai (println "divi:mistral:run-evo:divi-diff \n\n" last-divi data)

                ;; TODO also add hash to widget settings so next run can compare new seetings/hash to existing hash
                ;; Maybe just make a specific `add-divination` query. keep :Action format just make Cypher cleaner
                (db/call db/create-divination {
                    :jinni_id jinni-id
                    :provider PROVIDER
                    :data data})
                ;; TODO!!! (if (not= (:hash widget) (:hash last-divi ) (db/call db/set-intentions widget))
                new-image)
            (catch Exception e
                (println "divine:mistral:generation:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER})
            )))


;; Step #1 
(defn get-new-analysis-prompt
    "@param current widget settings for jinni
    @param last divination action + prompt
    check if intentions have changed since last divination
    if so then get new embeddings otherwise return last values
    returns old if no prompt should be generated or [prompt, embeds[]] if new intentions
    
    Example repsonse: `Based on your intentions of 'sexy physique' your phyiscal activity levels
        were insufficient leading to muscular atrophy however you did a lot a commits to github
        which was not in your intentions so did not affect experiment results`
    @returns 
    TODO "
    [settings divi]
    (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
            new-hash (hash inputs)
            reprompt? (or (not= new-hash (:hash divi)) (nil? (:hash divi)))]
    (println "divine:mistral:new-prompt:hash" reprompt? (:hash settings) (:hash divi))
    (if true ; (not reprompt?)
        {} ;; if existing divi and same settings, return existing prompt
        ;; (try ;; TODO#0 kinda want it to error out if prompt fails so no need to handle nil in other code paths
         (let [
            ;; TODO#1 embeds in neo4j with APOC plugin
            ;; embed-response (client/post "https://api.mistral.ai/v1/embeddings"
            ;; (assoc (portal/oauthed-request-config (:mistral-api-key (load-config)))
            ;;     :body (map->json {
            ;;             :model "mistral-embed"
            ;;             :input inputs
            ;;         })))
            ;; aaaa (println "divi:mistral:new-prompt:embeds" embed-response)
            ;; embeds (reduce #(concat %1 (:embedding %2)) [] (:data (json->map (:body embed-response))))
            ;; aaaa (println "divi:mistral:new-prompt:embeds" embeds)
            ;; asfas  (println "FUCK ME WE REPROMPTED")
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
            {:hash new-hash :prompt analysis-prompt :embeds []}) ;; TODO#1 :embed embeds
        ;; TODO#0 (catch Exception e
        ;;     (println "divine:mistral:new-prompt:ERR" e)
        ;;     (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
        ;;     [nil nil]))) ;; failure to generate new prompt. do not proceed with evolution
    )))

;; Step #0
(defn see-current-me
    "Checks if player can run a new evolution.
    Checks if player divinanation settings have changed and new prompts are needed.
    Fetchs all data since last evolution.
    Passes prompts and data to analysis/generation engine
    Returns url of most uptodate jinni image"
    [jinni-id]
    (let [divi-meta (db/call db/get-last-divination {:jinni_id jinni-id})
        {:keys [action widget]} divi-meta ; meta cant be null bc must have widget
        last-divi-time (or (:start_time action db/PORTAL_DAY))
        kkk (println "divine:mistral:see-current:time-since-last:" (:start_time action) last-divi-time)
        run-evo? (compare last-divi-time MIN_DIVINATION_INTERVAL_SEC)] ;; TODO probs throws nil error on divi-meta
        (println "divine:mistral:see-current:run-evo+setttings:" run-evo? widget)
      ;; (nil? (:image divi))  {:status 400 :error "No pfp for jinn on last divination"} ;; shouldnt be possible but just in case
            ;; TODO Add 3 days to start_time, probs direct java. add to utils.core
            ;; convert to UNIX, + MIN_DIVINATION_INTERVAL_SEC, convert back to ISO
        (if false ; TODO (= 1 (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            (get-last-divi-img jinni-id) ; dont run evolutions more than once every 3 days.
            (try (let [
                new-prompt (get-new-analysis-prompt widget (:hash divi))
                ;; aaa (println "divine:mistral:see-current:divi:" divi)
                updated-widget (merge widget new-prompt)

                aaaa (println "divi:mistral:see-current:new-divi  \n\n" updated-widget)
                at-taqa (:actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time  last-divi-time :end_time (now)}))
                sample-data (take 5 at-taqa) ;; sample for testing to not use too much context and run up bills
                ;; new-me (run-evolution jinni-id widget updated-widget actions)
                new-me (run-evolution jinni-id updated-widget action sample-data)
                path (save-divi-img jinni-id new-me (now))]
                aaaa (println "divi:mistral:see-current:save-new-me  \n\n" jinni-id path)

                ;; TODO update jinni widget  (if (:prompt new-prompt) (db/call ))

                 ;; TODO might need to strip /resource/. Might be better to return url from (save-divi-img) seems 
                ;; (str "https://" (:api-domain (load-config)) "/" path))
                path) ;; TODO file or path or url ?
            (catch Exception e
                (println "divine:mistral:see-current:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER}))))))

(defn get-jinn-img-handler
  "get the latests image for a jinn, reads the file on this server
  and serves it for display purposes on frontend <img> tags
  @DEV: requires GET request  w/ query param argument ?jid=xxxx-xxxx-xx-xxxx-xxx"
  [request]
  (let [jid (get-in request [:path-params :jid])
        [path (see-current-me jid)]
    (println "view pfp handler" jid path)
    (cond
      (nil? jid)            {:status 400 :error "must provide jinn id"}
      (nil? path)           {:status 404 :error "Jinn does not exist"} ; if no default base img then no player
      (.exists (io/file path))
            (-> (io/file path) ; send img directly to save locally for offline access. Player can also share directly to socials then
                (io/input-stream)
                (assoc :headers {"Content-Type" "image/png"})
                (ring.util.response/response))
      :else                 {:status 400 :body "Unknown divination error"})))
