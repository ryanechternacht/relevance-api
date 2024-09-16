(ns standby-api.middleware.db)

;; TODO probaby use component or mount for this
;; TODO probably setup a connection pool for this (c3p0?)

; This form has the advantage that changes to wrap-debug-impl are
; automatically reflected in the handler (due to the lookup in `wrap-db`)
(defn- wrap-db-impl [handler {{pg-db :pg-db} :config :as request}]
  (handler (assoc request :db pg-db)))

(defn wrap-db [h] (partial #'wrap-db-impl h))