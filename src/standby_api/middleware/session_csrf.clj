(ns standby-api.middleware.session-csrf
  "Adapted from default ring session middleware. We need to set 2 cookies
   (the session and a csrf token generated with the session), so I copied + modified."
  (:require [ring.middleware.cookies :as cookies]
            [ring.middleware.session.store :as store]
            [ring.middleware.session.memory :as mem]))

(defn- session-options
  [options]
  {:store        (options :store (mem/memory-store))
   :session-cookie-name  (options :session-cookie-name "ring-session")
   :session-cookie-attrs (merge {:path "/"
                                 :http-only true}
                                (options :session-cookie-attrs)
                                (if-let [root (options :root)]
                                  {:path root}))
   :csrf-cookie-name (options :csrf-cookie-name "csrf-token")
   :csrf-cookie-attrs (merge (options :csrf-cookie-attrs)
                             {:path "/"
                              :http-only false})})

(defn- bare-session-request
  [request {:keys [store session-cookie-name]}]
  (let [req-key  (get-in request [:cookies session-cookie-name :value])
        session  (store/read-session store req-key)
        session-key (if session req-key)]
    (merge request {:session (or session {})
                    :session/key session-key})))

(defn session-request
  "Reads current HTTP session map and adds it to :session key of the request.
  See: wrap-session."
  {:added "1.2"}
  ([request]
   (session-request request {}))
  ([request options]
   (-> request
       cookies/cookies-request
       (bare-session-request options))))

(defn- bare-session-response
  [response
   {session-key :session/key csrf-token :session-csrf-token}
   {:keys [store
           session-cookie-name session-cookie-attrs
           csrf-cookie-name csrf-cookie-attrs]}]
  (let [new-session-key (if (contains? response :session)
                          (if-let [session (response :session)]
                            (if (:recreate (meta session))
                              (do
                                (store/delete-session store session-key)
                                (->> (vary-meta session dissoc :recreate)
                                     (store/write-session store nil)))
                              (store/write-session store session-key session))
                            (if session-key
                              (store/delete-session store session-key))))
        session-attrs (:session-cookie-attrs response)
        session-cookie {session-cookie-name
                        (merge session-cookie-attrs
                               session-attrs
                               {:value (or new-session-key session-key)})}
        new-csrf-token (:session-csrf-token response)
        csrf-cookie {csrf-cookie-name (merge csrf-cookie-attrs
                                             {:value new-csrf-token})}
        response (dissoc response :session :session-cookie-attrs)]
    (if (or (and new-session-key (not= session-key new-session-key))
            (and session-attrs (or new-session-key session-key)))
      (assoc response :cookies (merge (response :cookies) session-cookie csrf-cookie))
      response)))

(defn session-response
  "Updates session based on :session key in response. See: wrap-session."
  {:added "1.2"}
  ([response request]
   (session-response response request {}))
  ([response request options]
   (if response
     (-> response
         (bare-session-response request options)
         cookies/cookies-response))))

(defn wrap-session
  "Reads in the current HTTP session map, and adds it to the :session key on
  the request. If a :session key is added to the response by the handler, the
  session is updated with the new value. If the value is nil, the session is
  deleted.

  Accepts the following options:

  :store        - An implementation of the SessionStore protocol in the
                  ring.middleware.session.store namespace. This determines how
                  the session is stored. Defaults to in-memory storage using
                  ring.middleware.session.store/memory-store.

  :root         - The root path of the session. Any path above this will not be
                  able to see this session. Equivalent to setting the cookie's
                  path attribute. Defaults to \"/\".

  :cookie-name  - The name of the cookie that holds the session key. Defaults to
                  \"ring-session\".

  :cookie-attrs - A map of attributes to associate with the session cookie.
                  Defaults to {:http-only true}. This may be overridden on a
                  per-response basis by adding :session-cookie-attrs to the
                  response."
  ([handler]
     (wrap-session handler {}))
  ([handler options]
     (let [options (session-options options)]
       (fn
         ([request]
          (let [request (session-request request options)]
            (-> (handler request)
                (session-response request options))))
         ([request respond raise]
          (let [request (session-request request options)]
            (handler request
                     (fn [response]
                       (respond (session-response response request options)))
                     raise)))))))
