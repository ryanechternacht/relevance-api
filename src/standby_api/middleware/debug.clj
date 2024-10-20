(ns standby-api.middleware.debug
  (:require [standby-api.middleware.config :as config]))

(defn- remove-annoying-field
  "this field is multiline by default and messes with our logs"
  [req]
  (update req :user dissoc :public-link-message))

(defn- remove-crsf-token
  [req]
  (cond-> req
    (associative? (:body req)) (update :body dissoc :csrf-token)))

;; WARNING setting print? to :local will dump the entire request map which includes
;; info that should not be logged. Don't use it in prod!
;; Setting debug? to :prod will only dump the request body

(defn- wrap-debug-request-impl [handler {:keys [config request-method uri] :as request}]
  (let [print? (-> config :debug :print?)]
    (condp = print?
      :prod (println "request" request-method uri (remove-crsf-token request))
      :local (println "request" request-method uri (remove-annoying-field request))
      nil))
  (handler request))

(defn wrap-debug-request [h] (partial #'wrap-debug-request-impl h))

;; Pulls from global config :/
(defn- wrap-debug-response-impl [handler request]
  (let [print? (-> config/config :debug :print?)
        response (handler request)]
    (condp = print?
      :local (println "response" response)
      nil)
    response))

(defn wrap-debug-response [h] (partial #'wrap-debug-response-impl h))
