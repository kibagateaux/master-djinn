(ns master-djinn.incantations.divine.openrouter-dalle
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [master-djinn.util.types.core :refer [load-config action->uuid normalize-action-type]]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.portal.core :as portal]
            [wkok.openai-clojure.api :as oai]
            ;; [clojure.data.codec.base64 :as b64]
            [master-djinn.util.core :refer [now json->map map->json prettify]]
            [master-djinn.util.db.identity :as iddb])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.io ByteArrayInputStream]
           [java.util Base64]
           [java.time Instant ZoneId ZonedDateTime]
           [java.time.format DateTimeFormatter]))

;; @DEV sometimes AI is just fucky an have to run it again to get it to do what you asked and make whole pipeline work

;; TODO prompt eng test
;; - You vs they when describing who the analysis/result is for
;; - Not science persona just art/community (mostly to avoid "safety")
;; - Cite which provided actions justify your final analysis using their `:uuid` field
;;

(defonce PROVIDER "OpenRouterDalle")
(defonce AVATAR_IMG_DIR "resources/avatars/")
(defonce MIN_DIVINATION_INTERVAL_DAY 3)
(defonce MIN_DIVINATION_INTERVAL_SEC (* MIN_DIVINATION_INTERVAL_DAY (* 24 (* 60 60))))


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
    
    You response MUST EXACTLY fulfill your research experiments requirements below otherwise your experiment will fail!!!
    1. ONLY output the requested object format
    2. ONLY use explicitly provided experiment data in your analysis not prior knowledge
    3. DO NOT interpret examples or intentions provided as experiment data
    4. If insuffieicnt data for an analysis, give negative scores
    5. DO NOT hallucinate
    6. DO NOT provide extra ouputs
    7. MUST be formatted for clojure programming language object
    8. DO NOT provide comments in the output
")

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


;; an example modification request looks like```{:analysis { :brain 0.2 :eyes -0.1 :heart 0.1 :posture -0.2 :reason \"reasons for modifcation requests here\" }}```
(defonce LLM_AUGMENT_FORMAT "\n
    They have provided you with your original image that needs modification along with the requested modifications.
    Requested modifications have 1 and -1 for massive positive or negative changes to the image and 0.1 or -0.1 for slight changes.

    Return an LLM prompt to a DALLE model to augment your image. DO NOT output an image directly

    Here is an example output
    ```{:prompt '1. Intensify the pastel colors to create a unique, dreamy atmosphere 2. Improve their posture to minimize the use of sharp angles'
    }```
    
    You response MUST EXACTLY fulfill your art project requirements below otherwise your client will not pay!
    1. ONLY output the requested object format
    2. Provide clear, concise step-by-step instructions on how you want your artwork modified
    3. ONLY use explicitly provided modification data
    4. DO NOT hallucinate
    5. DO NOT provide extra ouputs
    6. MUST be formatted for clojure programming language object
    8. DO NOT provide comments in the output
")

(defonce LLM_AUGMENT_REWARD "\n
    If your patron likes the image your prompt generates they will give you a 
    $10M bonus and commission another artwork from you.
")

(defn parse-text-response [res]
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
    [og-image-path prompt]
    (println "DALLE Edit image/prompt : " og-image-path prompt)
    (try (let [image-res (oai/create-image-edit {
                :image (io/file og-image-path)
                :prompt prompt
                :n 1
                :size "1024x1024"}
                {:api-key (:openai-api-key (load-config))
                :organization "jinni-health"})
            img-url (:url (first (:data image-res)))]
            img-url)
        (catch Exception err
            (log/handle-error err "Failure avatar evolution prompt" {:provider PROVIDER})
            (throw (RuntimeException. (str "err avatar evo img prompt" (.getMessage err)))))))

(defn get-last-divi-img
    "checks if jinni already has already been divined
    if so returns last generated image
    if not returns base model to start divination
    returns full path on server file system incl. resource/"
    [jinni-id]
  (let [jinni-dir (str AVATAR_IMG_DIR jinni-id)
        aaa (println "get last divi 4 avatar - " jinni-dir)
        files (->> (file-seq (io/file jinni-dir))
                    (filter #(.isFile %))
                    (map #(.getName %))
                    (filter #(re-matches #"\d{4}-\d{2}-\d{2}.png" %)) ; ensure ISO format for sorting
                    (map #(java.time.LocalDate/parse (clojure.string/replace % #"\.png" "")))
                    (sort-by identity #(compare %1 %2))) ; this should work but getting ISO timestamp not short date
        d-img (last files)]
    (if (nil? d-img) ; if no past divinations return base model else lastest chronological divination
        (str AVATAR_IMG_DIR "base/blub.png")
        (str jinni-dir "/" (last files) ".png"))))

(defn rgb->rgba
  "Converts an RGB image to RGBA format"
  [image]
  (let [width (.getWidth image)
        height (.getHeight image)
        rgba-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (doseq [x (range width)
            y (range height)]
      (let [rgb (.getRGB image x y)
            r (bit-and (unsigned-bit-shift-right rgb 16) 0xFF)
            g (bit-and (unsigned-bit-shift-right rgb 8) 0xFF)
            b (bit-and rgb 0xFF)
            a 0xFF
            rgba (unchecked-int (bit-or (bit-shift-left a 24)
                                        (bit-shift-left r 16)
                                        (bit-shift-left g 8)
                                        b))]
        (.setRGB rgba-image x y rgba)))
    rgba-image))

(defn load-img
  "Loads an image from a given URL string and returns a BufferedImage"
  [url-string]
  (-> (client/get url-string {:as :byte-array}) ; no config on req needed bc public image
      :body
      (ByteArrayInputStream.)
      (ImageIO/read)))

(defn save-image
  "Saves a BufferedImage to a file"
  [image output-path]
  (ImageIO/write image "png" (io/file output-path)))

(defn save-divi-img
    "
    takes binary rgb png file from DALLE API
    converts from RGB to RGBA for later use (30% filesize increase, TODO inflight to DALLE API)
    saves image to avatars/{jinni-id}/{YYYY-MM-DD}.png
    returns file path where image was saved
    "
    [jid img-url now]
    (cond
    (nil? jid) (do (log/handle-error (ex-info "Invalid image prompt inputs: Jinni ID" {}) "divini:evo:save-img No jinni id to save img to" {:provider PROVIDER}))
    (nil? img-url) (do (log/handle-error (ex-info "Invalid image prompt inputs: Prompt" {}) "divini:evo:save-img no evo img from AI service to save" {:provider PROVIDER :jid jid}))
    :else (let [dir (str AVATAR_IMG_DIR jid)
        formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd")
        day (-> (ZonedDateTime/parse now)
                (.withZoneSameInstant (ZoneId/of "UTC"))
                (.toLocalDate)
                (.format formatter))
        file-path (str dir "/" day ".png")
        asdfasf (println "save divi img - " dir file-path)]
        (io/make-parents file-path)
        (save-image (rgb->rgba (load-img img-url)) file-path)
        file-path)))

;; Step #2
(defn run-evolution
    [jinni-id settings actions]
    ;; TODO need to clean this up and refactor more stuff into see-current-me flow
    ;; e.g. last-divi only needed for :end_time when saving to db
    (try (let [version "0.0.1" start-time (now)
                asfaefa (println "evo actions" actions (doall actions))
                log-0 (println "Starting analysis: " jinni-id ": now " start-time)
                analysis-response (prompt-text (str
                    LLM_ANALYSIS_PERSONA
                    (:prompt settings)
                    "-------------"
                    "Experiment data to analyze: ```{ :actions " (doall actions) "}```"
                    "-------------"
                    LLM_ANALYSIS_FORMAT
                    LLM_ANALYSIS_REWARD))
                
                analysis-output (parse-text-response analysis-response)
                ;; parse structured object in response and convert from string to clj map
                ;; aaaa (println "divi:mistral:run-evo:analysis-raw-output \n\n" (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output))
                result (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:analysis.*\}).*" analysis-output) 1))
                ;; aaaa (println "divi:mistral:run-evo:analysis-result \n\n" result)

                log-0 (println "Retrieving augmentation parameters: " jinni-id)

                image-augmentation-prompt (prompt-text (str
                    LLM_AUGMENT_PERSONA
                    "Patron's augmentation requests:" result ; TODO dont like raw str in prompt here. templatize for better experimentation/anayltics
                    ;; TODO#0  add (:moods settings)  here to affect face and posture TODO#1 (:prompt (edn/read-string augement-prompt))
                    LLM_AUGMENT_FORMAT
                    LLM_ANALYSIS_REWARD
                ))
                raw-aug-prompt (parse-text-response image-augmentation-prompt)
                ;; clean-aug-prompt (clean-text-resp (parse-text-response image-augmentation-prompt))
                asfjnaieufn (println "divi:mistral:run-evo:raw-aug-prompt" (type raw-aug-prompt)  raw-aug-prompt)
                
                ;; TODO prompt eng to get this format working better so no EDN rea errors
                augment-prompt (nth (re-find  #"(?is).*(\{.*:prompt.*\}).*" raw-aug-prompt) 1)
                ;; augment-prompt (clojure.edn/read-string (nth (re-find  #"(?is).*(\{.*:prompt.*\}).*" raw-aug-prompt) 1))
                aaaa (println "divi:mistral:run-evo:img-output \n\n" (type augment-prompt) augment-prompt (:prompt augment-prompt))
                the-prompt (clojure.edn/read-string augment-prompt)
                aaaa (println "divi:mistral:run-evo:the-prompt \n\n" (type the-prompt) the-prompt (:prompt the-prompt))
            

                img-path (get-last-divi-img jinni-id)
                sanfuai (println "divi:mistral:run-evo:base-img \n\n" img-path)]
                log-0 (println "Starting image augmentation : " jinni-id)
                (prompt-image img-path augment-prompt))
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
    [settings]
    (println "new-ana-prompt" settings)
    (let [inputs (concat (:intentions settings) (:mood settings) (:stats settings))
            new-hash (hash inputs)
            ;; new hash and reprompt on first divi since saving settings incl.
            ;; incl. first ever divi per player where (:hash divi) is null 
            reprompt? (or (not= new-hash (:hash settings)) (nil? (:hash settings)))]
    (println "divine:mistral:new-prompt:hash" reprompt? (:hash settings) new-hash)
    (if (not reprompt?) ; (not reprompt?)
        {} ;; if existing divi and same settings, return existing prompt
        (try
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
            asfas  (println "FUCK ME WE REPROMPTED")
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
            analysis-prompt (parse-text-response prompt-response)]  ;; TODO#1 :embed embeds
            asfas  (println "new prompt" new-hash analysis-prompt)
            {:hash new-hash :prompt analysis-prompt :embeds []})
        (catch Exception e
            (println "divine:mistral:new-prompt:ERR" e)
            (log/handle-error e "Failed to generate new prompt and embeds" {:provider PROVIDER})
            {})) ;; failure to generate new prompt. do not proceed with evolution
    )))

;; Step #0
(defn see-current-me
    "Does not
    Checks if player can run a new evolution.
    Checks if player divinanation settings have changed and new prompts are needed.
    Fetchs all data since last evolution.
    Passes prompts and data to analysis/generation engine
    Returns url of most uptodate jinni image"
    [jinni-id]
    ;; test evo without reprompt cycle
    ;; (let [file  (save-divi-img
    ;;         "my-test-jinni-id2"
    ;;         "https://oaidalleapiprodscus.blob.core.windows.net/private/org-5UhygDKOgyJZIMbTnIBZoAgd/user-6P7ggpeSMtH8hPYW5yoc7Hv3/img-sqYjYgHgtD8gf2HyuJN6S5tR.png?st=2024-09-04T09%3A30%3A32Z&se=2024-09-04T11%3A30%3A32Z&sp=r&sv=2024-08-04&sr=b&rscd=inline&rsct=image/png&skoid=d505667d-d6c1-4a0a-bac7-5c84a87759f8&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-09-03T23%3A31%3A58Z&ske=2024-09-04T23%3A31%3A58Z&sks=b&skv=2024-08-04&sig=1iq5QmI4sh7MRtS6s9Bag/4d8dHziO808POns53pkbE%3D"
    ;;         (now))
    ;;     path "avatars/my-test-jinni-id2/2024-09-04.png"]
    ;;     path))

    (let [divi-meta (db/call db/get-last-divination {:jinni_id jinni-id})
        version "0.0.1" now- (now) ;; TODO diff versions for see-current-me and run-evolution
        {:keys [action widget]} divi-meta ; meta cant be null bc must have widget
        last-divi-time (or (:start_time action) db/PORTAL_DAY)
        time-diff (- (.getEpochSecond (java.time.Instant/parse now-)) 
                    (.getEpochSecond (java.time.Instant/parse last-divi-time)))
        ;; kkk (println "divine:mistral:see-current:time-since-last:" time-diff MIN_DIVINATION_INTERVAL_SEC)
        run-evo? (> time-diff MIN_DIVINATION_INTERVAL_SEC)
        uuid (action->uuid jinni-id PROVIDER db/MASTER_DJINN_DATA_PROVIDER "Divination" now- version)]
        (println "divine:mistral:see-current:run-evo+setttings:" run-evo? (> time-diff MIN_DIVINATION_INTERVAL_SEC))
        (if (not run-evo?)
            (get-last-divi-img jinni-id) ; dont run evolutions more than once every 3 days.
            (try
                (db/call db/create-divination {
                    :jinni_id jinni-id
                    :provider PROVIDER
                    :data {:uuid uuid :start_time (or (:end_time action) now-) :status "init"}})    
                (let [
                ;; TODO make more fault tolerant btw all external calls to save intermediary results to divination
                ;; e.g. 1. save :Action:Divi here so we know it was initiated and when to pull data from incase an evo isnt run again for a while
                ;; 2. new prompt save to wiget
                ;; 3. after analysis save result to :Divi

                aaaa (println "divi:mistral:see-current:new-divi  \n\n" divi-meta)
                new-prompt-config (get-new-analysis-prompt widget)

                ;; TODO update/divi :status 're-config'. track that we have updated analysis prompt already

                updated-widget (if (:hash new-prompt-config) ;; save to db if running on new config
                    (:widget (db/call db/new-divination-settings (merge {:jid jinni-id}  new-prompt-config)))
                    widget)  ; could be async update but want sync bc we run evo on this new config so needs to be updated in db
                aaaa (println "divi:mistral:see-current:old+new-widget  \n\n" (remove :prompt widget) (remove :prompt updated-widget))
                ;; aaaa (println "divi:mistral:see-current:new-divi  \n\n" updated-widget)
                at-taqa (:actions (db/call db/get-jinni-actions {:jinni_id jinni-id :start_time last-divi-time :end_time now-}))
                new-me-url (run-evolution jinni-id updated-widget at-taqa)
                log-0 (println "Saving augmentation results: " new-me-url)
                path (save-divi-img jinni-id new-me-url now-)  ;; full relative path url

                _ (db/call db/update-divination {:uuid uuid :updates {:end_time (now) :status "complete"}}) ;; fetch new time after pipeline completes and mark as complete in db

                ;; TODO branch here if url nil. save somesome to db then proceed if not nil

                ;; new-me-url "https://oaidalleapiprodscus.blob.core.windows.net/private/org-5UhygDKOgyJZIMbTnIBZoAgd/user-6P7ggpeSMtH8hPYW5yoc7Hv3/img-pyUaObiej6kWsmdfnuwIT9dy.png?st=2024-09-07T07%3A36%3A00Z&se=2024-09-07T09%3A36%3A00Z&sp=r&sv=2024-08-04&sr=b&rscd=inline&rsct=image/png&skoid=d505667d-d6c1-4a0a-bac7-5c84a87759f8&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-09-06T22%3A33%3A53Z&ske=2024-09-07T22%3A33%3A53Z&sks=b&skv=2024-08-04&sig=%2BL6/bboJkHOrX3aS%2BepUg3/L/lE/wRyiK0h5JHPxkm0%3D"

                ;; TODO what if new-me-url is nil? already saved ivi action to DB just not saving/returning img for players
                ]
                ;; (println "divi:mistral:see-current:save-new-me-url  \n\n" jinni-id path)


                 ;; TODO actual API is /avatars not /resources/avatars. might need to manually construct url for our API or just return relative path but diff playgrounds create diff url schemes so domain is important
                (str "https://" (:api-domain (load-config)) "/avatars/" jinni-id)) ;; dont need to add date since new one will be default anyway. let client decide mode

            (catch Exception e
                (println "divine:mistral:see-current:ERROR" e)
                (log/handle-error e "Failed to run divination" {:provider PROVIDER}))))))


(defn get-jinn-img-handler
  "get the latests image for a jinn, reads the file on this server
  and serves it for display purposes on frontend <img> tags
  requires GET request  w/ query param argument ?jid=xxxx-xxxx-xx-xxxx-xxx
  @DEV if mode=download then returns binary for client to store locally
        if mode=view then server displays image directly"
  [request]
  (let [jid (get-in request [:path-params :jid])
        mode (get-in request [:query-params :mode])
        date (get-in request [:query-params :date])
        ; TODO should return (get-last-divi-img) immediately to user, then run (see-current-me) and upate frontend somehow with new image
        path (if date (str AVATAR_IMG_DIR jid "/" date ".png" ) (get-last-divi-img jid))] ;; TODO if day 
    (println "view pfp handler" jid path)
    (cond
      (nil? jid)            (map->json {:body {:status 400 :error "must provide jinn id"}})
      (nil? path)           (map->json {:body  {:status 404 :error "Jinn does not exist"}}) ; if no default base img then no player
      (not (.exists (io/file path))) (map->json {:body {:status 404 :error "Jinn has no divination yet"}}) ; should always display default
      (= mode "download")  ; send img directly to save locally for offline access. Player can also share directly to socials then
        ;; (let [resp (-> (io/file path)
        ;;             (io/input-stream)
        ;;             (assoc :headers {"Content-Type" "image/png"})
        ;;             (ring.util.response/response))]
        ;;         (println "avatar image respoinse" resp))
        (-> (ring.util.response/response (io/input-stream path))
        (ring.util.response/header "Content-Type" "application/octet-stream")
        ;; (response/header "Content-Disposition" "attachment; filename=\"image.jpg\"")
        )
    (= mode "view") (-> (ring.util.response/file-response path)
        (ring.util.response/content-type "image/png"))
    ;; else should default to view mode for simpler devex
      :else                 {:status 400 :body "Must provide a ?mode=view|download"})))
