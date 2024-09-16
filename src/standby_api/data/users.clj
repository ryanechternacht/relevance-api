(ns standby-api.data.users
  (:require [clojure.string :as str]
            [honey.sql.helpers :as h]
            [standby-api.db :as db]
            [standby-api.utilities :as u]))

(def user-columns
  [:user_account.email :user_account.mail_sync_status
   :user_account.first_name :user_account.last_name
   :user_account.image :user_account.public_link])

(defn- base-user-query []
  (-> (apply h/select user-columns)
      (h/from :user_account)
      (h/order-by :user_account.first_name :user_account.last_name)))

(defn get-by-email [db email]
  (let [query (-> (base-user-query)
                  (h/where [:= :user_account.email email]))]
    (->> query
         (db/->>execute db))))
        ;;  first)))

(defn update-user-from-stytch [db email first-name last-name image]
  (try 
    (let [updates (cond-> {}
                    (not (str/blank? first-name)) (update :first_name first-name)
                    (not (str/blank? last-name)) (update :last_name last-name)
                    (not (str/blank? image)) (update :image image))]
      (-> (h/update :user_account)
          (h/set updates)
          (h/where [:= :email email])
          (db/->execute db)))
    (catch Exception _
      ;; if we fail, whatever just keep going
      nil)))

;; TODO base these off of name
(defn- get-public-link []
  (u/get-nano-id 7))

(defn create-user [db email first-name last-name image]
  (let [public-link (get-public-link)
        query (-> (h/insert-into :user_account)
                  (h/columns :email :first_name :last_name :image :public_link)
                  (h/values [[email first-name last-name image public-link]])
                  (#(apply h/returning % user-columns)))]
    (->> query
         (db/->>execute db)
         first)))
