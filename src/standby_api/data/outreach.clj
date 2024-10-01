(ns standby-api.data.outreach
  (:require [honey.sql.helpers :as h]
            [standby-api.db :as db]
            [standby-api.utilities :as u]))

(def outreach-columns
  [:outreach.uuid :outreach.recipient
   :outreach.sender :outreach.snippet
   :outreach.body :outreach.company_type
   :outreach.linkedin_url :outreach.calendar_url
   :outreach.company_name :outreach.company_logo_url
   :outreach.status :outreach.created_at])

(defn- base-outreach-query []
  (-> (apply h/select outreach-columns)
      (h/from :outreach)
      (h/order-by [:outreach.uuid :desc])))

(defn get-by-user
  ([db email]
   (get-by-user db email nil))
  ([db email statuses]
   (let [query (-> (base-outreach-query)
                   (h/where [:= :outreach.recipient email])
                   (cond-> statuses (h/where
                                     [:in :outreach.status statuses])))]
     (->> query
          (db/->>execute db)))))

(comment 
  (get-by-user db/local-db "ryan@sharepage.io" nil)
  (get-by-user db/local-db "ryan@sharepage.io" ["archive"])
  (get-by-user db/local-db "ryan@sharepage.io" ["archive" "new"])
  ;
  )

(defn create-outreach [db
                       {:keys [sender recipient snippet body
                               company-type linkedin-url
                               calendar-url company-name
                               company-logo-url]}]
  (let [query (-> (h/insert-into :outreach)
                  (h/columns :uuid :recipient :sender :snippet :body
                             :company_type :linkedin_url :calendar_url 
                             :company_name :company_logo_url)
                  (h/values [[(u/uuid-v7) recipient sender snippet body
                              company-type linkedin-url calendar-url
                              company-name company-logo-url]])
                  (merge (apply h/returning outreach-columns)))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (create-outreach db/local-db {:sender "tom@swaypage.io"
                                :recipient "ryan@sharepage.io"
                                :snippet "some cool outreach"
                                :body "much more content about this outreach"
                                :company-type "services"
                                :linkedin-url "asdf"
                                :calendar-url "ddd"
                                :company-name "company"
                                :company-logo-url "https://lh3.googleusercontent.com/a/ACg8ocI5zLIDRt1CiSH6jGV7a1K901iKcSzL9WqXYmdS1XaVn-jQHw=s96-c"})
  ;
  )

(defn update-outreach [db uuid body]
  (let [fields (select-keys body [:status])
        query (-> (h/update :outreach)
                  (h/set fields)
                  (h/where [:= :outreach.uuid (java.util.UUID/fromString uuid)])
                  (merge (apply h/returning (concat (keys fields) [:uuid]))))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (update-outreach db/local-db "0191fdf7-ae27-7a30-80ed-527390b7fa5b" {:status "archive"})
  ;
  )
