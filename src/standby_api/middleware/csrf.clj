(ns standby-api.middleware.csrf
  "Ring middleware to prevent CSRF attacks."
  (:require ;; [ring.middleware.anti-forgery.strategy :as strategy]
            [crypto.equality :as crypto]
            [ring.util.http-response :as response]))

;; adapted from and simplified from ring anti-forgery middleware
;; The csrf token is set our modified session middleware when a session is created

;; (defn- session-token [request]
;;   (get-in request [:session :csrf-token]))

;; (deftype CsrfStrategy []
;;   strategy/Strategy
;;   (get-token [_ request]
;;     (session-token request))

;;   (valid-token? [_ request token]
;;     (when-let [stored-token (session-token request)]
;;       (crypto/eq? token stored-token)))

;;   (write-token [_ request _ _]
;;     request))

;; (defn- get-req-token [request]
;;   (-> request :body :csrf-token))

;; (defn- get-request? [{method :request-method}]
;;   (or (= method :head)
;;       (= method :get)
;;       (= method :options)))

;; (defn- valid-request? [strategy request read-token]
;;   (or (get-request? request)
;;       (when-let [token (read-token request)]
;;         (strategy/valid-token? strategy request token))))

;; (defn- wrap-csrf-impl
;;   [handler request]
;;   (let [strategy (->CsrfStrategy)]
;;     (if (valid-request? strategy request get-req-token)
;;       (handler request)
;;       (response/forbidden "<h1>Invalid anti-forgery token</h1>"))))

;; (defn wrap-csrf [h] (partial #'wrap-csrf-impl h))

;; I tried breaking apart the routes so that I could implement the middleware above
;; and have it only apply to some routes, but I failed. (for some reason I ended up
;; with a nil for :body when the middleware would get the request). So we'll
;; implement this per-route for now. 

(defn csrf-failure-response []
  (response/forbidden {:error "invalid csrf token"}))

(defn valid-csrf? [{{session-csrf-token :csrf-token} :session {request-csrf-token :csrf-token} :body :as req}]
  (crypto/eq? session-csrf-token request-csrf-token))
