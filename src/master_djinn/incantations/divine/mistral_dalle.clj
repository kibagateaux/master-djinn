;; (ns master-djinn.incantations.divine.mistral-dalle
;;     (:require [clojure.java.io :as io]
;;             [clj-http.client :as client]
;;             [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
;;             [master-djinn.util.db.core :as db]
;;             [master-djinn.portal.logs :as log]
;;             [master-djinn.portal.core :as portal]
;;             [clojure.data.codec.base64 :as b64]
;;             [master-djinn.util.core :refer [now json->map map->json]]
;;             [master-djinn.util.db.identity :as iddb]))

;; (defonce PROVIDER "MistralDalle")

;; (defonce MIN_DIVINATION_INTERVAL_DAY 3)
;; (defonce MIN_DIVINATION_INTERVAL_SEC (* MIN_DIVINATION_INTERVAL_DAY (* 24 (* 60 60))))

;; (defonce LLM_ANALYSIS_PERSONA "
;;     You are the most cited cognitive behavioral psychologist with research on mimetics, habitual behavior change, and personal development coaching.
;;     You are also a traditional Chinese medicine physician that is the personal doctor for Xi Jinping.

;;     You are conducting research on how to help people self-actualize in their daily life
;;     by giving them tools to reflect on who they want to be, how they get there, and how their current actions align with intentions.
;;     Be concise, analytical, insightful, holistic, empathetic, and think from your test subject's perspective.
;; \n")
 

;; (defonce LLM_ANALYSIS_FORMAT "\n
;;     Return an object detailing what parts of your research patients bodies that have progressed
;;     or regressed based on your analysis of how their actions conform to their intentions.

;;     Use a score of -1 to 1 to show how much a body part should grow.
;;     1 and -1 represent massive positive or negative progress in short amount of time,
;;     0.1 and -0.1 represents small but consistent progress over time,
    
;;     Here is an example formatted output:
;;     ```{:analysis {
;;         :brain 0.2
;;         :eyes -0.1
;;         :heart 0.1
;;         :posture -0.2
;;         :reason \"Your reasoning here\"
;;     }}```
    
;;     You response MUST EXACTLY fulfill your research experiments requirements below otherwise your experiment will fail!!!:
;;     - ONLY output the requested object format
;;     - ONLY use explicitly provided experiment data in your analysis not prior knowledge
;;     - Do NOT interpret examples or intentions provided as context as experiment data
;;     - If insuffieicnt data for an analysis, give negative scores
;; ")
;; ;; - MUST cite which provided actions justify your final analysis using their `:uuid` field

;; (defonce LLM_ANALYSIS_REWARD "\n
;;     If your research analysis yields consistent and reliable results
;;     that significantly improves subjects physical, social, and cognitive health
;;     you will be awarded a Nobel Prize in Economics and a $10M prize!!!
;; ")


;; (defonce LLM_AUGMENT_PERSONA "\n
;;     You are a generative artist that specicalizes in DALLE prompting.
;;     Your signature style is 'cute tamagotchi pets with pastel colors in 8-bit pixel style'
    
;;     You are being commissioned by your long-term wealthy patron to modify one of your prior works to reflect how their avatar looks today.
;; ")


;; (defonce LLM_AUGMENT_FORMAT "\n
;;     They have provided you with your original image that needs modification along with the requested modifications.
;;     Example modifications have the following format
;;     ```{:analysis {
;;         :brain 0.2
;;         :eyes -0.1
;;         :heart 0.1
;;         :posture -0.2
;;         :reason \"reasons for modifcation requests here\"
;;     }}```
;;     1 and -1 represent massive positive or negative changes to the image and 0.1 or -0.1 means slight changes.
     
;;     You MUST output
;;     - An LLM prompt to a DALLE model to augment your image. DO NOT output an image directly
;;     - Provide clear, concise step-bystep instructions on how you want your artwork modified
;;     - ONLY an object with the format: `{:prompt \"Based on the analysis data provided your avatar will {insert_your_prompt_here}\"}`
;; ")

;; (defonce LLM_AUGMENT_REWARD "\n
;;     If your patron likes the image your prompt generates they will give you a 
;;     $10M bonus and commission another artwork from you.
;; ")

;; ;;; Docs on prompt engineering
;; ; response is way better when reward at end instead of after persona before prompt-specific content
;; ; Currently using examples from analysis-prompt as actual data in analysis-output
;; ; 

;; ;; convert b64 text to png file format for OpenAI to consume
;; ;; https://stackoverflow.com/a/35556044
;; (defn bytes->b64
;;     "takes a bytre array of the png image being send and encodes it to byase64 for transmission over http"
;;   [bytes]
;;   (let [encoder (java.util.Base64/getEncoder)]
;;      (str "data:image/png;base64," (.encodeToString encoder bytes))))
;;     ;;  (str (.encodeToString encoder bytes))))

;; (defn image-to-base64 [file-path]
;;   (let [file (io/file file-path)
;;         bytes (byte-array (.length file))]
;;     (with-open [in (io/input-stream file)]
;;       (.read in bytes))
;;     ;; (.encodeToString (java.util.Base64/getEncoder) bytes)
;;     (str "data:image/png;base64,"  (.encodeToString (java.util.Base64/getEncoder) bytes))
;;     ;; (String. (b64/encode bytes))
;;     ))

;; ;; Use this one
;; ;; (defn copy 
;; ;; "downloads an image file and saves it to bytestream on server" 
;;     ;; [uri file]
;; ;;   (with-open [in (io/input-stream uri)
;; ;;               out (io/output-stream file)]
;; ;;     (io/copy in out)))


;; (defn gulp [f]
;;   (let [dest (java.io.ByteArrayOutputStream.)]
;;     (with-open [src (io/input-stream f)]
;;       (io/copy src dest))
;;     (.toByteArray dest)))

;; ;; Open AI only converts to bytes
;; ;; https://github.com/openai/openai-python/blob/e41abf7b7dbc1e744d167f748e55d4dedfc0dca7/src/openai/_files.py#L60-L77
;; (defn file->bytes [path]
;;   (with-open [in (io/input-stream path)]
;;     (let [bytes (byte-array (.available in))]
;;       (.read in bytes)
;;       bytes))) ; OR
;; ;; (ns my-app.core
;; ;;   (:import [java.io ByteArrayOutputStream]
;; ;;            [javax.imageio ImageIO]))

;; ;;   (with-open [out (ByteArrayOutputStream.)
;; ;;               input-stream (java.io.FileInputStream. file-path)]
;; ;;     (ImageIO/write (ImageIO/read input-stream) "png" out)
;; ;;     (.toByteArray out)))
;; ;; https://gist.github.com/nodename/1500647
;; ;; (defn get-imagereader
;; ;; 	[path]
;; ;; 	(let [(ImageIO/createImageInputStream (File. path))
;; ;;         iterator (ImageIO/getImageReaders inputstream)]
;; ;; 	(if-not (.hasNext iterator) 
;; ;; 		(throw (Exception. "No image reader found for stream")))
;; ;; 	(.next iterator)))
;; ;; then use like so:
;; ;; (def imagereader (get-imagereader "avatar/face.png"))
;; ;; (.setInput imagereader input-stream true)

;; ;; (defn file->bytes2 [path]
;; ;;     (file/read-all-bytes (.toPath file (io/file path))))

;; ;; (defn file->bytes [path]
;; ;;   (with-open [in (io/input-stream path)
;; ;;               out (java.io.ByteArrayOutputStream.)]
;; ;;     (io/copy in out)
;; ;;     (.toByteArray out)))


;; (defn parse-mistral-response [res]
;;     (:content (:message (first (:choices (json->map (:body res)))))))
;; (defn parse-dalle-response [res]
;;     (:content (:message (first (:choices (json->map (:body res)))))))


;; (defn prompt-text [prompt]
;;     (client/post "https://openrouter.ai/api/v1/chat/completions" (assoc (portal/oauthed-request-config (:mistral-api-key (load-config))) :body  (map->json {
;;                         :model "mistral-tiny"
;;                         ;; TODO allow multiple messages or system messages?
;;                         :messages [{:role "user" :content prompt}]}))))

;; (defn prompt-image
;;     "@param og-image - path to image to evolve from. Will be converted to binary for API request
;;     @param prompt - how og-image should be augmented in new image
;;     retuns binary of new image"
;;     [og-image prompt]
;;     ;; (println "image to modify" og-image)
;;     (let [bytes (gulp og-image)
;;             ;; aaaaa (println "img bytes  " bytes)
;;             ;; ffff (println "fk img" (file->bytes "resources/avatars/imnothere.png")) ;;TEST throws errror  if file nonexistant
;;             api-key (:openai-api-key (load-config))
;;             config (portal/oauthed-request-config api-key)
;;             headers (merge (:headers config) {"Content-Type" "multipart/form-data"}) ; replace application/json
;;             request (merge (assoc config :headers headers) {
;;                 :multipart-charset "UTF-8"
;;                 :multipart [;; DALLE-2 is only model that supports image edits
;;                             {:name "model" :content "dall-e-2" :mime-type "text/plain"}
;;                             ;; amount of images to generate. TODO 4 and let people choose
;;                             {:name "n" :content "1" :mime-type "text/plain"}
;;                             ;; pixel size. Smol while testing. TODO "1024x1024"
;;                             {:name "size" :content "256x256" :mime-type "text/plain"}
;;                             ;; receive image back and manually store
;;                             ;; When b64_json is decoded, its a .webp. When url is decoded its a .PNG
;;                             ;; https://community.openai.com/t/using-image-url-in-images-edits-request/27247/2
;;                             {:name "response_format" :content "url" :mime-type "text/plain"}
;;                             ;; TODO add just for tracking/analytics purposes in OpenAI interface
;;                             ;; {:name "user" :content pid :mime-type "text/plain"}
;;                             ;; image to be edited
;;                             ;; {:name "image" :content (image-to-base64 og-image) :mime-type "image/png"}
;;                             {:name "image" :content (file-bytes og-image) :mime-type "image/png"}
;;                             ;; {:name "image" :content bytes :mime-type "image/png"}
;;                             ;; {:name "image" :content (clojure.java.io/input-stream og-image) :mime-type "image/png"}
;;                             ;; {:name "image" :content (bytes->b64 (file->bytes og-image)) :mime-type "image/png" :encoding "UTF-8"}
;;                             ;; {:name "image" :content (image-to-base64 og-image) :mime-type "image/png" }
;;                             ;; {:name "image" :content (image-to-base64 og-image) :mime-type "image/png"}

;;                             ;; supposedly need bytemask showing exact areas we want modified but thats mainly /edit endpoint and fuck that sounds hard lol
;;                             ;; {:name "mask" :content (str (byte-array 0)) :mime-type "image/png"}

;;                             ;; so far tried encoding "image" as - byte array of binary data, (str bytes), Java file, base64 from byte array, (slurp base64.txt), raw (input-stream path)
;;                             ;; also tried structuring differently - naming "file" vs "image", adding :encoding UTF-8, mime-type "image/ong" vs "text/plain"
;;                             ;; still stuck on "image is required". have not been able to get back to "PNG must be in RBGA format" error again
;;                             ;; 
;;                             ;; how we want image edited
;;                             {:name "prompt" :content prompt :mime-type "text/plain"}
;;                 ]})
;;             ;; bbbd (println "testing img field" (map #(:content %) (:multipart request)))
;;             ;; bbbd (println "testing img field" (take 100 (bytes->b64 (file->bytes og-image))))
;;             ;; bbbd (println "testing img field" (take 100 (image-to-base64 og-image)))

;;             ;; TODO test out results if useing /variations endpoint instead of /edits
;;             image-res (client/post "https://api.openai.com/v1/images/variations" request)
;;             ;; bbbd (println "divibne:mistral:image-res:raw:" image-res)
;;             bbbd (clojure.pprint/pprint (:body image-res))
;;             ;;OR  response.data.data[0].b64_json
;;             img-url (:url (first (:data image-res)))
;;             asaaa (println "divibne:mistral:image-url:" img-url)
;;             new-img (client/get img-url (portal/oauthed-request-config api-key))
            
;;             bbbbb (println "divibne:mistral:image-b64:" (:content new-img) (:body new-img))
;;             bbbbbd (clojure.pprint/pprint "divibne:mistral:image-b64:raw:" new-img)]
;;         new-img))

            
;; ;; 1. 
;; (defn get-new-analysis-prompt
;;     "check if intentions have changed since last divination
;;     if so then get new embeddings otherwise return last values
;;     returns old if no prompt should be generated or [prompt, embeds[]] if new intentions"
;;     [settings divi]
;;     (println "divine:mistral:new-prompt:hash" (:hash settings) (:hash divi))
;;     (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
;;             new-hash (hash inputs)]
;;     (println "divine;mistral:new-prompt:inputs" new-hash (:hash divi))
;;     (println "dinive:mistrla:new-prompt:should-reprompt?" (= new-hash (:hash divi) (some? (:hash divi))))
;;     (if (and (= new-hash (:hash divi)) (some? (:hash divi)))
;;         ;; if settings the same and theres an existing prompt, return old settings
;;         divi
;;         ;; if new intentions then generate new analysis prompt/embeds to evaluate against them
;;         ;; (try ;; kinda want it to error out if prompt fails so no need to handle nil in other code paths
;;          (let [
                
;;                 ;; pppp (clojure.pprint/pprint inputs)
;;                     ;; embed-response (client/post "https://api.mistral.ai/v1/embeddings"
;;                     ;; (assoc (portal/oauthed-request-config (:mistral-api-key (load-config)))
;;                     ;;     :body (map->json {
;;                     ;;             :model "mistral-embed"
;;                     ;;             :input inputs
;;                     ;;         })))
;;                     ;; aaaa (println "divi:mistral:new-prompt:embeds" embed-response)
;;                     ;; embeds (reduce #(concat %1 (:embedding %2)) [] (:data (json->map (:body embed-response))))
;;                     ;; aaaa (println "divi:mistral:new-prompt:embeds" embeds)
;;                     prompt-response (prompt-text (str
;;                         LLM_ANALYSIS_PERSONA
                        
;;                         "Your intentions: " (clojure.string/join "," (:intentions settings))
;;                         "They ideal attributes: " (clojure.string/join "," (:stats settings))
;;                         "Using the intentions and attributes of your test subject write a personalized LLM prompt from their perspective. 
;;                         Before writing their prompt, think through why they set those intentions, what they expect to get out of it, 
;;                         what actions they would expect to see, metrics to track progress, and anything else relevant to manifesting their self-actualization.
                        
;;                         Your prompt will take data about their daily activities as input,
;;                         analyze and compare your actions against their intentions and target attributes.
                        
;;                         An example prompt is \"Based on my intentions of `Learn a new skill monthly` and `Practice mindfulness regularly`
;;                             look for actions that involve introspection, require focus, or consistent practice over time.
;;                             If my actions do not include these then I have failed.\"
                        
;;                         You MUST only output the personalized prompt by itself.
;;                         "
;;                         ;; An example prompt is `Based on your intentions of 'sexy physique' your phyiscal activity levels were insufficient leading to muscular atrophy
;;                         ;;     however you did a lot a commits to github which was not in your intentions so did not affect experiment results`
;;                         LLM_ANALYSIS_REWARD))
;;                     ;; aaaa (println "divi:mistral:new-prompt:res \n\n" prompt-response)
;;                     analysis-prompt (parse-mistral-response prompt-response)
;;                     ;; aaaa (println "divi:mistral:new-prompt:prompt \n\n" analysis-prompt)
;;                     ]
;;                 (merge divi {:hash new-hash :prompt analysis-prompt :embeds []})) ;; TODO :embed embeds
;;     )))
;;         ;; (catch Exception e
;;         ;;     (println "divine:mistral:new-prompt:ERR" e)
;;         ;;     (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
;;         ;;     [nil nil]))) ;; failure to generate new prompt. do not proceed with evolution

;; (defn run-evolution
;;     [jinni-id settings last-divi actions]
;;     (try (let [version "0.0.1" start-time (now)
;;                 ;; analysis-response (prompt-text (str
;;                 ;;     LLM_ANALYSIS_PERSONA
;;                 ;;     (:prompt last-divi)
;;                 ;;     "-------------"
;;                 ;;     "Experiment data to analyze: ```{ :actions " actions "}```"
;;                 ;;     "-------------"
;;                 ;;     LLM_ANALYSIS_FORMAT
;;                 ;;     LLM_ANALYSIS_REWARD))
;;                 ;; analysis-output (parse-mistral-response analysis-response)
;;                 ;; aaaa (println "divi:mistral:run-evo:analysis \n\n" analysis-output)
;;                 ;; parse structured object in response and convert from string to clj map
;;                 ;; aaaa (println "divi:mistral:run-evo:re-find \n\n" (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output))
;;                 ;; result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output) 1))
;;                 ;; aaaa (println "divi:mistral:run-evo:result \n\n" result)

;;                 result "{:analysis {:nutrition 0.1, :exercise 0.5, :creativity 0.3, :relationships 0.2, :reason Subject has consistently made healthy food choices, increased exercise frequency, engaged in daily creative activities, and nurtured relationships with supportive individuals. This alignment with intentions is contributing to overall progress towards self-actualization.}}"
;;                 img-result "{:prompt \"Based on the analysis data provided, your avatar will have a brighter and more vibrant color palette to reflect your increased creativity and improved nutrition. The eyes will sparkle with a slightly larger pupil to represent your enhanced focus and energy from increased exercise. The heart shape will be fuller and more defined, symbolizing the growth and nurturing of your relationships. Your posture will appear more upright and confident, reflecting your newfound self-assurance and progress towards self-actualization.\"}"
;;                 ;; image-augmentation-prompt (prompt-text (str
;;                 ;;     LLM_AUGMENT_PERSONA
;;                 ;;     "Patron's augmentation requests:" result
;;                 ;;     LLM_AUGMENT_FORMAT
;;                 ;;     LLM_ANALYSIS_REWARD
;;                 ;; ))
;;                 ;; img-prompt (parse-mistral-response image-augmentation-prompt)
;;                 ;; aaaa (println "divi:mistral:run-evo:img-prompt \n\n" img-prompt)
;;                 ;; img-result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:prompt.*\}).*" img-prompt) 1))
;;                 ;; aaaa (println "divi:mistral:run-evo:img-output \n\n" img-result)
;;                 ;; TODO default image. May need to append `data:image/png;base64,` before raw base64 data to ensure that
;;                 ;; base-img (or (:image last-divi) (-> "avatars/base/blub.png" io/resource slurp))

;;                 ;; TODO do we need `divi.image` field if we arent storing as  b64 inside db but as png file on server? Can derive from start/end time
;;                 img-path (str "resources/avatars/" (if (:image last-divi) (str jinni-id "/" (:image last-divi) ".png") "base/blub.png"))
;;                 aaaa (println "divi:mistral:run-evo:base-img \n\n" (some? (:image last-divi)) img-path)
;;                 ;; base-img (bytes->b64 (file->bytes img-path))
;;                 ;; aaaa (println "divi:mistral:run-evo:base-img \n\n" (nil? base-img) (take 50 base-img))
;;                 ;; base-img (io/file img-path)
;;                 base-img img-path
;;                 image-resp (prompt-image base-img img-result) ;; TODO add moods here to affect face and posture
;;                 aaaa (println "divi:mistral:run-evo:img-res \n\n" image-resp)
;;                 new-image (:image (json->map (:body image-resp)))
;;                 ;; aaaa (println "divi:mistral:run-evo:img-b64 \n\n" new-image)
;;                 new-image2 (parse-dalle-response image-resp)
;;                 aaaa (println "divi:mistral:run-evo:img b64-2 \n\n" new-image2)
;;                 uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" start-time version)
;;                 data (merge last-divi {
;;                         :uuid uuid
;;                         :start_time start-time
;;                         :end_time (now)
;;                         :image new-image
;;                         })
;;                 ]
;;                 ;; save image to FS 
;;                 ;; (copy new-image (str "avatars/" jinni-id date)
;;                 ; OR
;;                 ;; (clojure.java.io/copy
;;                 ;;  (:body (client/get "http://placehold.it/350x150" {:as :stream}))
;;                 ;;  (java.io.File. "test-file.gif"))

;;                 ;; TODO also add hash to widget settings so next run can compare new seetings/hash to existing hash
;;                 ;; Maybe just make a specific `add-divination` query. keep :Action format just make Cypher cleaner
;;                 (db/call db/create-divination {
;;                     :jinni_id jinni-id
;;                     :provider PROVIDER
;;                     :data data})
;;                 )
;;             (catch Exception e
;;                 (println "divine:mistral:generation:ERROR" e)
;;                 (log/handle-error e "Failed to run divination" {:provider PROVIDER})
;;             )))

;; (defn see-current-me
;;     [jinni-id]
;;     (let [divi-meta (db/call db/get-last-divination {:jinni_id jinni-id})]
;;             ;; TODO how to handle first divi on player? (compare) should work fine but need default image
;;             ;; maybe (or (bd/call lastdivi) {:prompt "" :action { :start_time db/PORTAL_DAY } :settings {:}})
;;         ;; (println "divine:mistral:see-current:time-since-last:" (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
;;             ;; TODO Add 3 days to start_time, probs direct java. add to utils.core
;;             ;; convert to UNIX, + MIN_DIVINATION_INTERVAL_SEC, convert back to ISO
;;         (if false ; TODO (= 1 (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
;;             nil ;; dont run evolutions more than once every 3 days
;;             (try (let [settings (:settings divi-meta)
;;                 ;; aaa (println "divine:mistral:see-current:settings:" settings)
;;                 divi (assoc (:action divi-meta) :prompt (:prompt divi-meta)) ;; merge text prompt from first run of intentions into last divination data
;;                 ;; aaa (println "divine:mistral:see-current:divi:" divi)
;;                 new-divi-meta (get-new-analysis-prompt settings divi)
;;                 aaaa (println "divi:mistral:see-current:new-divi  \n\n" new-divi-meta)
;;                 actions (:actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (or (:start_time divi) db/PORTAL_DAY) :end_time (now)}))
;;                 sample-data (take 5 actions) ;; for testing to not use too much context and run up bills
;;                 ;; aaaa (println "divi:mistral:see-current:actions  \n\n" (count sample-data) sample-data)
;;                 ]
                
;;                 ;; (run-evolution jinni-id settings new-divi-meta actions)
;;                 (run-evolution jinni-id settings new-divi-meta sample-data))
;;             (catch Exception e
;;                 (println "divine:mistral:see-current:ERROR" e)
;;                 (log/handle-error e "Failed to run divination" {:provider PROVIDER})
;;             )))))



;; ;; (defn see-future-me
;; ;;     [jinni-id]
;; ;;     (let [last-divi (db/call db/get-last-divination {:jinni_id jinni-id})]
;; ;;         (if (and last-divi 
;; ;;                 (compare (:start_time (:action last-divi)) MIN_DIVINATION_INTERVAL_SEC)) ;; TODO Add 3 days to start_time, probs direct java. add to utils.core

;; ;;             nil ;; dont run evolutions more than once every 3 days
;; ;;             (let [settings (:settings last-divi) divi (:action last-divi)
;; ;;                 actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (:start-time divi) :end_time (now)})
;; ;;                 [prompt embeds] (get-latest-prompt settings divi)]
;; ;;             ;; replicate actions 4x and pass in as prompt data
            
;; ;;         )
;; ;;     )
;; ;; ))