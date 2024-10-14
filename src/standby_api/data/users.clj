(ns standby-api.data.users
  (:require [clojure.string :as str]
            [honey.sql.helpers :as h]
            [standby-api.db :as db]
            [standby-api.utilities :as u]))

(def default-profile-message-template (slurp "resources/default-profile-message.mustache"))

(def user-columns
  [:user_account.email :user_account.mail_sync_status
   :user_account.first_name :user_account.last_name
   :user_account.image :user_account.public_link
   :user_account.public_link_message :user_account.relevancies 
   :user_account.onboarding_step :user_account.has-send-scope])

(defn- base-user-query []
  (-> (apply h/select user-columns)
      (h/from :user_account)
      (h/order-by :user_account.first_name :user_account.last_name)))

(defn get-by-email [db email]
  (let [query (-> (base-user-query)
                  (h/where [:= :user_account.email email]))]
    (->> query
         (db/->>execute db)
         first)))

(defn get-by-shortcode [db shortcode]
  (let [query (-> (base-user-query)
                  (h/where [:=
                            [:lower :user_account.public_link]
                            [:lower shortcode]]))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (get-by-shortcode db/local-db "QYP2n1i")
  ;
  )

(defn update-user-from-stytch [db email {:keys [first-name last-name image
                                                has-send-scope refresh-token
                                                provider-values]}]
  (try
    (let [updates (cond-> {}
                    (not (str/blank? first-name)) (assoc :first_name first-name)
                    (not (str/blank? last-name)) (assoc :last_name last-name)
                    (not (str/blank? image)) (assoc :image image)
                    refresh-token (assoc :refresh_token refresh-token)
                    ;; only update has send scope if it's a new refresh token
                    refresh-token (assoc :has_send_scope has-send-scope)
                    provider-values (assoc :provider_values (db/lift provider-values)))
          query (-> (h/update :user_account)
                    (h/set updates)
                    (h/where [:= :email email]))]
      (db/execute db query))
    (catch Exception _
      ;; if we fail, whatever just keep going
      nil)))

;; TODO base these off of name
(defn- get-public-link [first-name last-name]
  (str first-name "-" last-name "-" (u/get-nano-id-lowercase 6)))

(defn create-user [db email {:keys [first-name last-name image provider-values
                                    has-send-scope refresh-token]}]
  (let [public-link (get-public-link first-name last-name)
        query (-> (h/insert-into :user_account)
                  (h/columns :email :first_name :last_name :image 
                             :public_link :public_link_message 
                             :oauth_token has-send-scope refresh-token)
                  (h/values [[email first-name last-name image
                              public-link default-profile-message-template
                              (db/lift provider-values) has-send-scope
                              refresh-token]])
                  (#(apply h/returning % user-columns)))]
    (->> query
         (db/->>execute db)
         first)))

(defn update-user [db email fields]
  (let [fields (-> fields (select-keys [:first-name
                                        :last-name
                                        :image
                                        :mail-sync-status
                                        :public-link
                                        :relevancies
                                        :onboarding-step])
                   (u/update-if-not-nil :relevancies db/lift))
        query (-> (h/update :user_account)
                  (h/set fields)
                  (h/where [:= :email email])
                  (merge (apply h/returning (concat (keys fields) [:email]))))]
    (->> query
         (db/->>execute db)
         first)))

(comment
  (update-user db/local-db "ryan@sharepage.io" {:mail-sync-status "setup-required"})
  ;
  )

(defn get-user-refresh-token!
  "These tokens are secure information that should be handled with care"
  [db email]
  (let [query (-> (h/select :refresh_token)
                  (h/from :user_account)
                  (h/where [:= :email email]))]
    (->> query
         (db/->>execute db)
         first
         :refresh_token)))

(defn check-public-link 
  "returns nil if the link is fine. a string describing the error otherwise"
  [link]
  (cond 
    (< (count link) 6) "Public links must be at least 6 characters"
    (> (count link) 40) "Public links must be fewer than 40 characters"
    (not (re-find #"^[a-zA-Z0-9\-]+$" link)) "Public links can only contain letters, numbers, and dashes (-)"))

(comment
  (check-public-link (str/join (repeat 50 "a")))
  (check-public-link "abc")
  (check-public-link "sadfdf_")
  (check-public-link "abc1234")
  ;
  )

(defn update-user-public-link
  "returns false if another user has this link, truthy otherwise"
  [db email link]
  (let [check-query (-> (h/select 1)
                        (h/from :user_account)
                        (h/where [:and
                                  [:= :public_link link]
                                  [:not= :email email]]))
        check-result (->> check-query
                          (db/->>execute db)
                          seq)]
    (if check-result
      false
      (update-user db email {:public-link link}))))

(comment
  (update-user-public-link db/local-db "ryan@sharepage.io" "asdf")
  (update-user-public-link db/local-db "tom@sharepage.io" "asdf")
  ;
  )
