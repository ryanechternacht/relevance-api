(ns standby-api.jobs.weekly-digest
  (:require [cljstache.core :as stache]
            [java-time.api :as jt]
            [standby-api.data.outreach :as outreach]
            [standby-api.db :as db]
            [standby-api.external-api.aws :as aws]
            [standby-api.middleware.config :as config]
            [standby-api.data.users :as users]))

(def ^:private sample-max 10)
(def ^:private weekly-digest-template (slurp "resources/weekly-digest.mustache"))
(def ^:private weekly-digest-subject "Weekly Relevance Reel")

(defn add-short-snippet [{:keys [snippet] :as outreach}]
  (assoc outreach :snippet-short (if (<= (count snippet) 50)
                                   snippet
                                   (str (subs snippet 0 50) "..."))))

(defn get-outreach-data [db email]
  (let [a-week-ago (jt/minus (jt/local-date) (jt/days 7))
        samples (->> (outreach/get-by-user db email {:after a-week-ago :has-emoji? true} {:limit sample-max})
                     (map add-short-snippet))
        unread-count (outreach/get-by-user db email {:status "new"} {:count? true})
        replied-count (outreach/get-by-user db email {:status "replied" :after a-week-ago} {:count? true})
        sample-count (count samples)
        other-unread-count (max 0 (- unread-count (count samples)))]
    {:samples samples
     :has-samples (boolean samples)
     :sample-count sample-count
     :sample-plural (> sample-count 1)
     :should-render-unread-other (> 0 other-unread-count)
     :other-unread-count other-unread-count
     :other-unread-count-plural (> other-unread-count 1)
     :replied-count replied-count
     :replied-count-plural (> replied-count 1)}))

(comment
  (get-outreach-data db/local-db "ryan@relevance.to")
  ;
  )

(defn send-user-weekly-digest!
  "sends the weekly digest email to the supplied user"
  [aws-config frontend-url db {:keys [email]}]
  (let [data (get-outreach-data db email)
        body (stache/render weekly-digest-template
                            (merge data {:frontend-eul frontend-url}))
        {:keys [cognitect.aws.http/status] :as result}
        (aws/send-email aws-config email weekly-digest-subject body)]
    (when status ;; only set on an error
      (throw (ex-info "AWS SES returned an error" result)))))

(comment
  (try
    (send-user-weekly-digest! (:aws config/config)
                              (-> config/config :front-end :base-url)
                              db/local-db
                              {:email "ryan@sharepage.io"})
    (catch Exception ex
      (println ex)))
  ;
  )

;; ;; a single argument is required for calling from deps.edn
(defn weekly-digest! 
  "This fn sends weekly digests to all users. BE CAREFUL"
  [_]
  (println "starting weekly digest job")
  (let [db (:pg-db config/config)
        aws (:aws config/config)
        front-end-base-url (-> config/config :front-end :base-url)
        users (users/get-all db)]
    (doseq [{:keys [email] :as user} users]
      (println "starting weekly digest for" email)
      (try
        (send-user-weekly-digest! aws front-end-base-url db user)
        (println "weekly digest successfully sent for" email)
        (catch Exception ex
          (println "error sending weekly digest to" email)
          (println ex)
          (println ex-data))))))

(comment
  (weekly-digest! {})
  ;
  )
