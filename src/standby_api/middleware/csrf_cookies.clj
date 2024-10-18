(ns standby-api.middleware.csrf-cookies
  (:require [ring.middleware.cookies :as cookies]))

;; TODO, this shouldn't need to send down CSRF every request -- ideally, it should
;; only do when the session cookie is generated, but to achieve that i'd have to
;; modify wrap-session and I don't want to currently
(defn- wrap-csrf-cookies-impl
  "adds the csrf cookie to the response and configures and applies 
   anti-forgery ring layer"
  [handler {{:keys [csrf-token]} :session {:keys [cookie-attrs]} :config :as request}]
  (let [response (handler request)]
    (-> response
        (assoc :cookies {"relevance-csrf-token" (merge cookie-attrs
                                                       {:value csrf-token
                                                        :path "/"
                                                        :http-only false})})
        (cookies/cookies-response response))))

(defn wrap-csrf-cookies [h] (partial #'wrap-csrf-cookies-impl h))
