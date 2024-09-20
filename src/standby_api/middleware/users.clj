(ns standby-api.middleware.users
  (:require [standby-api.data.users :as users]
            [standby-api.utilities :as u]))

(defn- wrap-user-impl [handler {:keys [session db] :as request}]
  (if-let [email (-> session :emails first :email)]
    (let [user (u/kebab-case
                (users/get-by-email db email))]
      (handler (assoc request :user user)))
    (handler request)))

; This form has the advantage that changes to wrap-debug-impl are
; automatically reflected in the handler (due to the lookup in `wrap-user`)
(defn wrap-user [h] (partial #'wrap-user-impl h))
