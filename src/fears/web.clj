(ns fears.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [cemerick.drawbridge :as drawbridge]
            [hiccup.core :refer :all]
            [hiccup.page :refer [include-css]]
            [clojure.java.jdbc :as db]
            [environ.core :refer [env]]))

(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/secret" []
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (html
               (include-css "resources/styles.css")
               [:h1 "Yes, very secret."]
               [:p "Blah blah."])})
  (GET "/add/:content" [content]
       {:status 200
        :body (do
                (db/insert! (env :database-url) :our_fears {:content content})
                (html
                 [:p (str "Added fear " content)]))})
  (GET "/fears" []
       {:status 200
        :headers {"Content-Type ""text/html"}
        :body (html
               (into []
                     (concat [:ul]
                             (map (fn [fear] [:li fear])
                                  (map :content
                                       (db/query (env :database-url) ["select content from our_fears;"]))))))})
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str ["Hellooo"])})
  (GET "/resources/:r" [r]
       (slurp (io/resource r)))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn wrap-app [app]
  ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
  (let [store (cookie/cookie-store {:key (env :session-secret)})]
    (-> app
        ((if (env :production)
           wrap-error-page
           trace/wrap-stacktrace))
        (site {:session {:store store}}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (wrap-app #'app) {:port port :join? false})))

;; For interactive development:
(comment
  (def server (-main))
  (.stop server)
  (env :foo)
  (env :database-url)
  )

;; (db/query "postgresql://localhost:5432/" ["select * from our_fears"])
;;"postgresql://localhost:5432/fears"
