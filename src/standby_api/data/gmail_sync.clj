(ns standby-api.data.gmail-sync
  (:require [honey.sql.helpers :as h]
            [standby-api.db :as db]))

(defn setup-auth [db email]
  (let [query (-> (h/insert-into :gmail_sync)
                  (h/columns :user_email)
                  (h/values [[email]])
                  (h/on-conflict :user_email)
                  (h/do-nothing))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (setup-auth db/local-db "ryan@relevance.to")
  ;
  )

(defn update-bookmark [db email sync]
  (let [fields (select-keys sync [:history-id :label-id :status])
        query (-> (h/update :gmail_sync)
                  (h/set fields)
                  (h/where [:= :user_email email])
                  (h/returning :user_email))]
    (->> query
         (db/->>execute db)
         first)))

(comment 
  (update-bookmark db/local-db "ryan@sharepage.io" {:history-id 3
                                                    :label-id "Label_6805839102955159862"})
  ;
  )

(defn get-active-syncs [db]
  (let [query (-> (h/select :user_email :status :history_id :label_id)
                  (h/from :gmail_sync)
                  (h/where [:= :is_enabled true]))]
    (->> query
         (db/->>execute db))))

(comment
  (get-active-syncs db/local-db)
  ;
  )

(defn get-for-user [db email]
  (let [query (-> (h/select :status :is_enabled :user_email :history_id :label_id)
                  (h/from :gmail_sync)
                  (h/where [:= :user_email email]))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (get-for-user db/local-db "ryan@relevance.to")
  ;
  )

(defn update-gmail-sync [db email updates]
  (let [fields (select-keys updates [:is-enabled])
        query (-> (h/update :gmail_sync)
                  (h/set fields)
                  (h/where [:= :user_email email])
                  (merge (apply h/returning (concat (keys fields) [:user_email]))))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (update-gmail-sync db/local-db "ryan@relevance.to" {:a 1 :is-enabled false})
  ;
  )