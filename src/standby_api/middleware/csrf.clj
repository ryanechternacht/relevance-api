(ns standby-api.middleware.csrf
  "Ring middleware to prevent CSRF attacks."
  (:require [ring.middleware.anti-forgery.strategy :as strategy]
            [crypto.equality :as crypto]
            [ring.util.http-response :as response]))

;; adapted from and simplified from ring anti-forgery middleware
;; The csrf token is set our modified session middleware when a session is created

(defn- session-token [request]
  (get-in request [:session :csrf-token]))

(deftype CsrfStrategy []
  strategy/Strategy
  (get-token [_ request]
    (session-token request))

  (valid-token? [_ request token]
    (when-let [stored-token (session-token request)]
      (crypto/eq? token stored-token)))

  (write-token [_ request _ _]
    request))

(defn- get-req-token [request]
  (-> request :body :csrf-token))

(defn- get-request? [{method :request-method}]
  (or (= method :head)
      (= method :get)
      (= method :options)))

(defn- valid-request? [strategy request read-token]
  (or (get-request? request)
      (when-let [token (read-token request)]
        (strategy/valid-token? strategy request token))))

(defn- wrap-csrf-impl
  [handler request]
  (let [strategy (->CsrfStrategy)]
    (if (valid-request? strategy request get-req-token)
      (handler request)
      (response/forbidden "<h1>Invalid anti-forgery token</h1>"))))

(defn wrap-csrf [h] (partial #'wrap-csrf-impl h))
