(ns standby-api.jobs.weekly-digest
  (:require [cljstache.core :as stache]
            [java-time.api :as jt]
            [standby-api.data.outreach :as outreach]
            [standby-api.db :as db]
            [standby-api.external-api.aws :as aws]
            [standby-api.middleware.config :as config]))

;; for a user
;; send email with these facts in them?

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
        body (stache/render weekly-digest-template (merge data
                                                          {:frontend-eul frontend-url}))]
    (aws/send-email aws-config email weekly-digest-subject body)))

(comment
  (send-user-weekly-digest! (:aws config/config)
                            (-> config/config :front-end :base-url)
                            db/local-db
                            {:email "ryan@relevance.to"})
  ;
  )
