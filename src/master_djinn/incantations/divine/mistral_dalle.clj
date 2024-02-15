(ns master-djinn.incantations.divine.mistral-dalle
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.core :refer [now json->map map->json]]
            [master-djinn.util.db.identity :as iddb]))

;; prompt pipeline
;; 1. generate prompt that will analyze actions
;;     inputs: intentions, stats

;; 2. generate prompt that will output changes in avatar
;;     inputs: analysis prompt, action data

;; 3. generate dalle prompt that makes variations to image based on changes
;;     inputs: changes, 

;; 4. DALLE generates variation of image


;; RETURNS
;; -  hash of avatar config + analysis prompt (saved in widget config so we dont have to regenerate analysis prompt everytime if config (mainly intentions) doesnt change
;; -  divination uuid
;; -  url of new image on DALLE
;; -  map of ± attributes/body parts
(defonce PROVIDER "MistralDalle")
(defonce INITIAL_DIVI {
    :settings {

    }
    :action {
        :start_time db/PORTAL_DAY
        :end_time db/PORTAL_DAY
    }
    :prompt nil
})
(defonce MIN_DIVINATION_INTERVAL_DAY 3)
(defonce MIN_DIVINATION_INTERVAL_SEC (* MIN_DIVINATION_INTERVAL_DAY (* 24 (* 60 60))))
(defonce LLM_PERSONA "
    You are a world renowned cognitive behavioral psychologist,
    traditional Chinese medicine physician that is private doctor for Xi Xi Ping,
    and a global adminstrator and teacher of Montessori Schools.
    You are conducting research on how to help people self-actualize in their daily life
    by giving them tools to reflect on who they want to be, how they get there, and how their current actions align with that.
    Be concise, analytical, insightful, holistic, empathetic, and think from your test subject's perspective.
\n")
 

(defonce LLM_ANALYSIS_FORMAT "\n
    Return an object detailing what parts of your research patients bodies that have progressed
    or regressed based on their actions taken compared to the intentions they told you.
    Use a score of -1 to 1 with 1 representing massive progress in short amount of time and
    0.1 representing small but consistent progress over time and -1 means massive performance regression.
    An example output object is:
    ```{:brain .2
        :eyes -.1
        :heart .1
        :arms .1
        :posture -0.2}```
    
    Only output the object otherwise your research will fail!
") ;; TODO add mapping of :Provider -> body parts + weightings?

(defonce LLM_REWARD "\n
    If your research analysis yields consistent and reliable results
    that signficanlty improves subjects physical, social, and cognitive health
    you will be awarded a Nobel Prize in Economics and a $10M prize!!!
")


(defonce LLM_AUGMENT_FORMAT "\n
    You are a generative artist that specicalizes in DALLE prompting.
    Your signature style is 'cute tamagotchi pets with pastel colors in 8-bit pixel style'
    
    You are being commissioned by ;; a government research institure to 
    Your client wants you to modify one of your prior works to reflect how their pet looks today
    They have provided you with 
    Generate a DALLE prompt that uses
    
    If your client likes your drawing they will give you a $10M bonus.
")

(defn prompt-text [prompt]
    (client/post "https://api.mistral.ai/v1/chat/completions" (assoc (portal/oauthed-request-config (:mistral-api-key (load-config))) :body  (map->json {
                        :model "mistral-tiny"
                        ;; TODO allow multiple messages or system messages?
                        :messages [{:role "user" :content prompt}]}))))

(defn prompt-image
    "@param og-image - image to evolve from. must be in binary format
    @param prompt - how og-image should be augmented in new image
    retuns binary of new image"
    [og-image prompt]
    (let [api-key (:openai-api-key (load-config))
            image-res (client/post "https://api.openai.com/v1/images/edits" (assoc (portal/oauthed-request-config api-key) :body  (map->json {
                        :model "dall-e-2" ;; only model that supports image edits
                        :n 1 ;; amount of images to generate TODO 4 and let people choose
                        :size "256x256" ;; pixel size. Small while testing in beta
                        ;; :size "1024x1024"
                        
                        ;; receive image back and manually store
                        ;; https://community.openai.com/t/using-image-url-in-images-edits-request/27247/2
                        :response_format "b64_json" 
                        
                        :image og-image
                        ;; TODO allow multiple messages or system messages?
                        :prompt prompt})))
            bbbd (clojure.pprint/pprint "divibne:mistral:image-res:raw:" image-res)
            img-url (:url (first (:data image-res)))
            asaaa (println "divibne:mistral:image-url:" img-url)
            b64-img (client/get img-url (portal/oauthed-request-config api-key))
            bbbbb (println "divibne:mistral:image-b64:" (:content b64-img) (:body b64-img))
            bbbbbd (clojure.pprint/pprint "divibne:mistral:image-b64:raw:" b64-img)]
        b64-img))

(defn run-evolution
    [jinni-id settings last-divi actions]
    (try (let [version "0.0.1" start-time (now)
                analysis-output (prompt-text (str LLM_PERSONA (:prompt last-divi) LLM_ANALYSIS_FORMAT LLM_REWARD))
                aaaa (println "divi:mistral:run-evo:analysis \n\n" analysis-output)
                ;; image-augmentation-prompt (prompt-text (str analysis-output LLM_ANALYSIS_FORMAT LLM_REWARD))
                ;; new-image (prompt-image (:image last-divi) image-augmentation-prompt) ;; TODO add moods here to affect face and posture
                uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" start-time version)
                ]
                ;; (db/call db/batch-create-actions [{
                ;;     :player_id jinni-id
                ;;     :provider PROVIDER
                ;;     :player_relation "SEES"
                ;;     :action_type "Divination"
                ;;     :data (merge {
                ;;         :uuid uuid
                ;;         :provider PROVIDER
                ;;         :start_time start-time
                ;;         :end_time (now)
                ;;         :image new-image} last-divi)}])
                        )
            (catch Exception e
                (println "divine:mistral:generation:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER})
            )))
            
;; 1. 
(defn get-new-prompt
    "check if intentions have changed since last divination
    if so then get new embeddings otherwise return last values
    returns old if no prompt should be generated or [prompt, embeds[]] if new intentions"
    [settings divi]
    (println "divine:mistral:new-prompt:hash" (:hash settings) (:hash divi))
    (if (and (= (:hash settings) (:hash divi) (some? (:hash divi))))
        divi
        ;; if new intentions then generate new analysis prompt to evaluate against them
        ;; (try ;; kinda want it to error out if prompt fails so no need to handle nil in other code paths
         (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
                new-hash (hash inputs)
                pppp (println "divine;mistral:new-prompt:inputs" inputs)
                ;; pppp (clojure.pprint/pprint inputs)
                    ;; TODO embeds later, not needed until we have a chatbot
                    ;; embeds (client/post "https://api.mistral.ai/v1/chat/embeddings"
                    ;; (assoc (portal/oauthed-request-config (:mistral-api-key (load-config))
                    ;;     :body (map->json {
                    ;;             :model "mistral-embed"
                    ;;             :input inputs
                    ;;         }))))
                    ;; aaaa (println "divi:mistral:new-prompt:embeds" embeds)
                    analysis-prompt (prompt-text (str
                        LLM_PERSONA
                        "Your research subject's intentions are: " (clojure.string/join "," (:intentions settings))
                        "They are optimizing for these attribute: " (clojure.string/join "," (:stats settings))
                        "Generate a personalized LLM prompt for this test subject that will take data about
                        their daily activities in a standard format as input, analyze and compare their actions
                        against their stated goals/intentions, and see if those actions improve their target attributes.
                        You MUST only output the personalized prompt by itself.
                        "
                        LLM_REWARD))
                    aaaa (println "divi:mistral:new-prompt:prompt \n\n" analysis-prompt)]
                (merge {:hash new-hash :prompt analysis-prompt :embeds []} divi)) ;; TODO :embed embeds
    ))
        ;; (catch Exception e
        ;;     (println "divine:mistral:new-prompt:ERR" e)
        ;;     (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
        ;;     [nil nil]))) ;; failure to generate new prompt. do not proceed with evolution

(defn see-current-me
    [jinni-id]
    (let [divi-meta (or (db/call db/get-last-divination {:jinni_id jinni-id}) INITIAL_DIVI)]
            ;; TODO how to handle first divi on player? (compare) should work fine but need default image
            ;; maybe (or (bd/call lastdivi) {:prompt "" :action { :start_time db/PORTAL_DAY } :settings {:}})
        ;; (println "divine:mistral:see-current:time-since-last:" (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            ;; TODO Add 3 days to start_time, probs direct java. add to utils.core
            ;; convert to UNIX, + MIN_DIVINATION_INTERVAL_SEC, convert back to ISO
        (if  false ;(= 1 (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            nil ;; dont run evolutions more than once every 3 days
            (try (let [settings (:settings divi-meta)
                aaa (println "divine:mistral:see-current:settings:"settings)
                divi (assoc (:action divi-meta) :prompt (:prompt divi-meta)) ;; merge text prompt from first run of intentions into last divination data
                new-divi-meta (get-new-prompt settings divi)
                actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (:start_time divi) :end_time (now)})]
                ;; (run-evolution jinni-id settings new-divi-meta actions)
                )
            (catch Exception e
                (println "divine:mistral:see-current:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER})
            )))))



;; (defn see-future-me
;;     [jinni-id]
;;     (let [last-divi (db/call db/get-last-divination {:jinni_id jinni-id})]
;;         (if (and last-divi 
;;                 (compare (:start_time (:action last-divi)) MIN_DIVINATION_INTERVAL_SEC)) ;; TODO Add 3 days to start_time, probs direct java. add to utils.core

;;             nil ;; dont run evolutions more than once every 3 days
;;             (let [settings (:settings last-divi) divi (:action last-divi)
;;                 actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (:start-time divi) :end_time (now)})
;;                 [prompt embeds] (get-latest-prompt settings divi)]
;;             ;; replicate actions 4x and pass in as prompt data
            
;;         )
;;     )
;; ))