(ns master-djinn.incantations.divine.mistral-dalle
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.core :refer [now json->map map->json]]
            [master-djinn.util.db.identity :as iddb]))

(defonce PROVIDER "MistralDalle")

(defonce MIN_DIVINATION_INTERVAL_DAY 3)
(defonce MIN_DIVINATION_INTERVAL_SEC (* MIN_DIVINATION_INTERVAL_DAY (* 24 (* 60 60))))

(defonce LLM_ANALYSIS_PERSONA "
    You are the most cited cognitive behavioral psychologist with research on mimetics, habitual behavior change, and personal development coaching.
    You are also a traditional Chinese medicine physician that is the personal doctor for Xi Jinping.

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
    
    You are being commissioned by a long-term wealthy benefactor to modify one of your prior works to reflect how their pet looks today.
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
     

     You must only output an object with the following format: `{:prompt \"insert_your_dalle_prompt_here\"}`
")

(defonce LLM_AUGMENT_REWARD "\n
    If your client likes your drawing they will give you a $10M bonus and commission another artwork from you.
")

;;; Docs on prompt engineering
; response is way better when reward at end instead of after persona before prompt-specific content
; Currently using examples from analysis-prompt as actual data in analysis-output
; 

(defn parse-mistral-response [res]
    (:content (:message (first (:choices (json->map (:body res)))))))

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

            
;; 1. 
(defn get-new-analysis-prompt
    "check if intentions have changed since last divination
    if so then get new embeddings otherwise return last values
    returns old if no prompt should be generated or [prompt, embeds[]] if new intentions"
    [settings divi]
    (println "divine:mistral:new-prompt:hash" (:hash settings) (:hash divi))
    (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
            new-hash (hash inputs)
            pppp (println "divine;mistral:new-prompt:inputs" new-hash (:hash divi))]
    (if (and (= new-hash (:hash divi) (some? (:hash divi))))
        ;; if settings the same and theres an existing prompt, return old settings
        divi
        ;; if new intentions then generate new analysis prompt/embeds to evaluate against them
        ;; (try ;; kinda want it to error out if prompt fails so no need to handle nil in other code paths
         (let [
                
                ;; pppp (clojure.pprint/pprint inputs)
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
                        
                        "Your intentions: " (clojure.string/join "," (:intentions settings))
                        "They ideal attributes: " (clojure.string/join "," (:stats settings))
                        "Using the intentions and attributes of your test subject write a personalized LLM prompt from their perspective. 
                        Before writing their prompt, think through why they set those intentions, what they expect to get out of it, 
                        what actions they would expect to see, metrics to track progress, and anything else relevant to manifesting their self-actualization.
                        
                        Your prompt will take data about their daily activities as input,
                        analyze and compare your actions against their intentions and target attributes.
                        
                        An example prompt is \"Based on my intentions of `Learn a new skill monthly` and `Practice mindfulness regularly`
                            look for actions that involve introspection, require focus, or consistent practice over time.
                            If my actions do not include these then I have failed.\"
                        
                        You MUST only output the personalized prompt by itself.
                        "
                        ;; An example prompt is `Based on your intentions of 'sexy physique' your phyiscal activity levels were insufficient leading to muscular atrophy
                        ;;     however you did a lot a commits to github which was not in your intentions so did not affect experiment results`
                        LLM_ANALYSIS_REWARD))
                    ;; aaaa (println "divi:mistral:new-prompt:res \n\n" prompt-response)
                    analysis-prompt (parse-mistral-response prompt-response)
                    ;; aaaa (println "divi:mistral:new-prompt:prompt \n\n" analysis-prompt)
                    ]
                (merge divi {:hash new-hash :prompt analysis-prompt :embeds []})) ;; TODO :embed embeds
    )))
        ;; (catch Exception e
        ;;     (println "divine:mistral:new-prompt:ERR" e)
        ;;     (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
        ;;     [nil nil]))) ;; failure to generate new prompt. do not proceed with evolution

(defn run-evolution
    [jinni-id settings last-divi actions]
    (try (let [version "0.0.1" start-time (now)
                analysis-response (prompt-text (str
                    LLM_ANALYSIS_PERSONA
                    (:prompt last-divi)
                    "-------------"
                    "Experiment data to analyze: ```{ :actions " actions "}```"
                    "-------------"
                    LLM_ANALYSIS_FORMAT
                    LLM_ANALYSIS_REWARD))
                analysis-output (parse-mistral-response analysis-response)
                ;; aaaa (println "divi:mistral:run-evo:analysis \n\n" analysis-output)
                ;; parse structured object in response and convert from string to clj map
                ;; aaaa (println "divi:mistral:run-evo:re-find \n\n" (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output))
                result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output) 1))
                aaaa (println "divi:mistral:run-evo:result \n\n" result)

                image-augmentation-prompt (prompt-text (str LLM_AUGMENT_PERSONA analysis-output LLM_AUGMENT_FORMAT LLM_ANALYSIS_REWARD))
                img-prompt (parse-mistral-response image-augmentation-prompt)
                aaaa (println "divi:mistral:run-evo:img \n\n" img-prompt)
                ;; image-res (prompt-image (:image last-divi) image-augmentation-prompt) ;; TODO add moods here to affect face and posture
                ;; new-image (:image (json->map (:body image-res)))
                uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" start-time version)
                data (merge last-divi {
                        :uuid uuid
                        :start_time start-time
                        :end_time (now)
                        ;; :image new-image
                        })
                ]
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

(defn see-current-me
    [jinni-id]
    (let [divi-meta (db/call db/get-last-divination {:jinni_id jinni-id})]
            ;; TODO how to handle first divi on player? (compare) should work fine but need default image
            ;; maybe (or (bd/call lastdivi) {:prompt "" :action { :start_time db/PORTAL_DAY } :settings {:}})
        ;; (println "divine:mistral:see-current:time-since-last:" (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            ;; TODO Add 3 days to start_time, probs direct java. add to utils.core
            ;; convert to UNIX, + MIN_DIVINATION_INTERVAL_SEC, convert back to ISO
        (if false ; TODO (= 1 (compare (:start_time (:action divi-meta)) MIN_DIVINATION_INTERVAL_SEC))
            nil ;; dont run evolutions more than once every 3 days
            (try (let [settings (:settings divi-meta)
                ;; aaa (println "divine:mistral:see-current:settings:" settings)
                divi (assoc (:action divi-meta) :prompt (:prompt divi-meta)) ;; merge text prompt from first run of intentions into last divination data
                ;; aaa (println "divine:mistral:see-current:divi:" divi)
                new-divi-meta (get-new-analysis-prompt settings divi)
                aaaa (println "divi:mistral:see-current:new-divi  \n\n" new-divi-meta)
                actions (:actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time (or (:start_time divi) db/PORTAL_DAY) :end_time (now)}))
                sample-data (take 5 actions) ;; for testing to not use too much context and run up bills
                ;; aaaa (println "divi:mistral:see-current:actions  \n\n" (count sample-data) sample-data)
                ]
                
                ;; (run-evolution jinni-id settings new-divi-meta actions)
                (run-evolution jinni-id settings new-divi-meta sample-data))
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