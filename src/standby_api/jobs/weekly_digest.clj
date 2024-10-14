(ns standby-api.jobs.weekly-digest
  (:require [cljstache.core :as stache]
            [java-time.api :as jt]
            [standby-api.db :as db]
            [standby-api.data.outreach :as outreach]))

;; for a user
;; send email with these facts in them?

(defn add-short-snippet [{:keys [snippet] :as outreach}]
  (assoc outreach :snippet-short (if (<= (count snippet) 50)
                                   snippet
                                   (str (subs snippet 0 50) "..."))))

(defn get-outreach-data [db email]
  (let [a-week-ago (jt/minus (jt/local-date) (jt/days 7))
        last-week-count (outreach/get-by-user db email {:after a-week-ago} {:count? true})
        samples (->> (outreach/get-by-user db email {:after a-week-ago} {:limit 4})
                     (map add-short-snippet))
        unread-count (outreach/get-by-user db email {:status "new"} {:count? true})
        replied-count (outreach/get-by-user db email {:status "replied" :after a-week-ago} {:count? true})]
    {:last-week-count last-week-count
     :last-week-plural (> last-week-count 1)
     :samples samples
     :unread-count unread-count
     :unread-count-plural (> unread-count 1)
     :replied-count replied-count
     :replied-count-plural (> replied-count 1)}))

(comment
  (get-outreach-data db/local-db "ryan@relevance.to")
  ;
  )

(let [template (slurp "resources/weekly-digest.mustache")
      data (get-outreach-data db/local-db "ryan@relevance.to")]
  (stache/render template (merge data
                                 {:frontend-url "http://app.buyersphere-local.com/"})))

