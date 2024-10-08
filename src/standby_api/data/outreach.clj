(ns standby-api.data.outreach
  (:require [honey.sql.helpers :as h]
            [standby-api.db :as db]
            [standby-api.utilities :as u]
            [standby-api.middleware.config :as config]
            [standby-api.external-api.gmail :as gmail]
            ;; [standby-api.data.gmail-sync :as gmail-sync]
            [standby-api.data.users :as users]
            ))

(def outreach-columns
  [:outreach.uuid :outreach.recipient
   :outreach.sender :outreach.snippet
   :outreach.body :outreach.company_type
   :outreach.linkedin_url :outreach.calendar_url
   :outreach.company_name :outreach.company_logo_url
   :relevant-emoji :relevant-description
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

(defn get-by-uuid [db uuid]
  (let [query (-> (base-outreach-query)
                  (h/where [:= :uuid (java.util.UUID/fromString uuid)]))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (get-by-uuid db/local-db "0191fdf7-ae27-7a30-80ed-527390b7fa5b")
  ;
  )

(defn create-outreach [db
                       {:keys [sender recipient snippet body
                               linkedin-url calendar-url
                               company-logo-url company-name
                               relevant-emoji relevant-description]}]
  (let [query (-> (h/insert-into :outreach)
                  (h/columns :uuid :recipient :sender :snippet :body
                             :linkedin_url :calendar_url
                             :company_name :company_logo_url
                             :relevant_emoji :relevant_description)
                  (h/values [[(u/uuid-v7) recipient sender snippet body
                              linkedin-url calendar-url
                              company-name company-logo-url
                              relevant-emoji relevant-description]])
                  (merge (apply h/returning outreach-columns)))]
    (->> query
         (db/->>execute db)
         first)))

(comment
   (create-outreach db/local-db {:sender "ryan@echternacht.org"
                                :recipient "ryan@relevance.to"
                                :snippet "Fitness for busy founders"
                                :body "Hi Ryan<br>I wanted to know if you're finding time to stay fit while being a founder. If you need help with motivation, time management, or exercise planning, I'd love to help you. <br>Regards,<br>Tom"
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

(defn reply!
  "Calling this method will create a draft email in the user's inbox!"
  [gmail-config db user uuid {:keys [message]}]
  (let [outreach (get-by-uuid db uuid)
        {:keys [refresh-token]} (users/get-user-oauth-tokens! db (:email user))
        {access-token :access_token} (gmail/get-access-token gmail-config refresh-token)]
    (gmail/create-outreach-reply-draft access-token user outreach message)
    (update-outreach db uuid {:status "replied"})))

(comment
  (reply! (:google-api config/config)
          db/local-db
          {:email "ryan@sharepage.io"
           :mail-sync-status "ready"}
          "0191fdf7-ae27-7a30-80ed-527390b7fa5b"
          {:message "hi there"})
  ;
  )
