(ns standby-api.server
  (:require [camel-snake-kebab.core :as csk]
            [standby-api.middleware.config :as m-config]
            [standby-api.middleware.db :as m-db]
            [standby-api.middleware.debug :as debug]
            ;; [standby-api.middleware.anonymous-users :as m-anon-users]
            ;; [standby-api.middleware.organization :as m-org]
            [standby-api.middleware.kebabify-params :as m-kebabify-params]
            ;; [standby-api.middleware.postwork :as m-postwork]
            [standby-api.middleware.stytch-store :as m-stytch]
            [standby-api.middleware.users :as m-users]
            [standby-api.routes :as r]
            ;; [standby-api.middleware.csrf :as csrf]
            [standby-api.middleware.session-csrf :as session-csrf]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :as m-cors]
            [ring.middleware.json :as m-json]
            [ring.middleware.keyword-params :as m-keyword-param]
            [ring.middleware.multipart-params :as m-multi-params]
            [ring.middleware.params :as m-params]))

(def session-store (m-stytch/stytch-store (:stytch m-config/config)
                                          (:pg-db m-config/config)))

(def handler
  (-> r/routes
      debug/wrap-debug-request
      ;; m-postwork/wrap-postwork
      m-kebabify-params/wrap-kebabify-params
      (m-json/wrap-json-body {:key-fn csk/->kebab-case-keyword})
      m-users/wrap-user
      ;; m-anon-users/wrap-anonymous-user
      ;; m-org/wrap-organization
      m-db/wrap-db
      m-config/wrap-config
      (session-csrf/wrap-session {:store session-store
                                  :session-cookie-attrs (:cookie-attrs m-config/config)
                                  :session-cookie-name "relevance-session"
                                  :csrf-cookie-name "relevance-csrf-token"
                                  :csrf-cookie-attrs (:cookie-attrs m-config/config)})
      m-keyword-param/wrap-keyword-params
      m-params/wrap-params
      m-multi-params/wrap-multipart-params
      m-json/wrap-json-response
      (m-cors/wrap-cors :access-control-allow-origin #".*"
                        :access-control-allow-methods [:get :patch :put :post :delete]
                        :access-control-allow-credentials "true")
      debug/wrap-debug-response)
      ;
  )

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn webserver [params]
  (jetty/run-jetty #'handler params))

#_(webserver {:port 3001
              :join? false})

;; TRY THIS: https://clojurians.slack.com/archives/C03S1KBA2/p1706551970190269?thread_ts=1706543519.076899&cid=C03S1KBA2
;; (defonce web-server (atom nil))

;; (defn boot []
;;   (swap! web-server
;;          (fn [s]
;;            (when s (.stop s))
;;            (run-jetty #'app {:join? false :port 8080}))))

;; (comment

;;   (boot))