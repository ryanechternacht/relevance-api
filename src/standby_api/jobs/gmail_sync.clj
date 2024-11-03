(ns standby-api.jobs.gmail-sync
  (:require [standby-api.middleware.config :as config]
            [standby-api.data.gmail-sync :as gmail-sync]
            [standby-api.external-api.gmail :as gmail]
            [standby-api.data.users :as users]
            [java-time.api :as jt]
            [standby-api.db :as db]))

(def ^:private relevance-label-name "Relevance")

(defn- should-mark-thread-as-outreach?
  "handles this email thread. if the thread doesn't have our label already, is from an external sender,
   and contains a sales email (using ai), we return true. otherwise, false"
  [access-token user-email user-signup-date label-id messages]
  (let [from-email (->> messages first :payload :headers (filter #(= "From" (:name %))) first :value gmail/parse-from-email)]
    (cond
      (> (count messages) 1) false
      (gmail/does-message-have-label? (first messages) label-id) false
      (gmail/is-sender-internal? user-email from-email) false
      (gmail/has-prior-correspondence-with-sender? access-token user-signup-date relevance-label-name from-email) false
      :else true)))

(comment
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (let [access-token <access-token>
        thread-id "192bb0ffc1c869e1"
        {:keys [messages]} (gmail/gmail-api-get access-token
                                                (str "users/me/threads/" thread-id))]
    (should-mark-thread-as-outreach? access-token
                                     "ryan@relevance.to"
                                     (java.sql.Timestamp/from (jt/instant "2024-10-05T18:31:06.925589000-00:00"))
                                     "Label_1330615818894066579"
                                     messages))
  ;
  )

(defn- process-new-email [front-end-base-url db access-token sync]
  (let [{:keys [history-id user-email label-id]} sync
        _ (println "Processing new email for" user-email)
        {user-created-at :created-at :as user} (users/get-by-email db user-email)
        
        threads (gmail/get-threads-after-history access-token history-id)]
    (doseq [{:keys [id]} threads]
      (println "Checking email" id)
      (let [{:keys [messages] :as full-thread}
            (gmail/gmail-api-get access-token
                                 (str "users/me/threads/" id))]
        (when (should-mark-thread-as-outreach? access-token user-email user-created-at label-id messages)
          (println "Marking thread as outreach" id)
          (gmail/send-relevance-response front-end-base-url access-token full-thread user)
          (gmail/archive-and-apply-our-label access-token id label-id))))
    (let [most-recent (gmail/get-largest-thread-history threads)]
      (gmail-sync/update-bookmark db user-email {:history-id most-recent}))))

;; TODO
(defn- initial-run [db access-token sync]
  (let [{:keys [user-email label-id]} sync
        _ (println "Setting up sync for" user-email)
        most-recent-thread (gmail/get-largest-thread-history
                            (gmail/get-threads access-token))]
    (when (not label-id)
      (let [{:keys [id]} (gmail/make-label access-token relevance-label-name)]
        (gmail-sync/update-bookmark db user-email {:label-id id})))
    (gmail-sync/update-bookmark db user-email {:history-id most-recent-thread :status "active"})))

(defn- sync-user! [gmail-config front-end-base-url db sync]
  (try
    (let [{:keys [status user-email]} sync
          _ (println "Starting sync for" user-email)
          refresh-token (users/get-user-refresh-token! db user-email)
          {access-token :access_token} (gmail/get-access-token gmail-config refresh-token)]
      (condp = status
        "active" (process-new-email front-end-base-url db access-token sync)
        "setup" (initial-run db access-token sync)
        (throw (Exception. "Sync in error state"))))
    (catch Exception ex
      ;; what else should we do other than just skip for now?
      (println (str "Sync failed for " (:user-email sync)))
      (println ex))))
  
(comment
  (sync-user! (-> config/config :google-api)
              (-> config/config :front-end :base-url)
              db/local-db
              (gmail-sync/get-for-user db/local-db "ryan@relevance.to"))
  ;
  )

;; ;; a single argument is required for calling from deps.edn
(defn gmail-sync! [_]
  (println "Gmail-sync starting")
  (let [db (:pg-db config/config)
        gmail (:google-api config/config)
        syncs (gmail-sync/get-active-syncs db)
        front-end-base-url (-> config/config :front-end :base-url)]
    (doseq [sync syncs]
      (sync-user! gmail front-end-base-url db sync))))

(comment
  (gmail-sync! {})
  ;
  )
