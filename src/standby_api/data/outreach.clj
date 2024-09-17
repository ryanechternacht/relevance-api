(ns standby-api.data.outreach
  (:require [honey.sql.helpers :as h]
            [standby-api.db :as db]
            [standby-api.utilities :as u]))

(def outreach-columns
  [:outreach.uuid :outreach.recipient
   :outreach.sender :outreach.snippet
   :outreach.body :outreach.company_type
   :outreach.created_at])

(defn- base-outreach-query []
  (-> (apply h/select outreach-columns)
      (h/from :outreach)
      (h/order-by [:outreach.uuid :desc])))

(defn get-by-user [db email]
  (let [query (-> (base-outreach-query)
                  (h/where [:= :outreach.recipient email]))]
    (->> query
         (db/->>execute db))))

(comment 
  (get-by-user db/local-db "ryan@sharepage.io")
  ;
  )

(defn create-outreach [db {:keys [sender recipient snippet body company-type]}]
  (let [query (-> (h/insert-into :outreach)
                  (h/columns :uuid :recipient :sender :snippet :body :company_type)
                  (h/values [[(u/uuid-v7) recipient sender snippet body company-type]])
                  (merge (apply h/returning outreach-columns)))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (create-outreach db/local-db {:sender "tom@swaypage.io"
                                :recipient "ryan@sharepage.io"
                                :snippet "some cool outreach"
                                :body "much more content about this outreach"
                                :company-type "services"})
  ;
  )