(ns kybernetik.routes.home
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.controllers.users :as kcu]
   [kybernetik.controllers.welcome :as kcw]
   [clojure.java.io :as io]
   [kybernetik.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render-template request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (layout/render-template request "about.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get kcw/index}]
   ["/users" {:get kcu/index
              :post kcu/create}]
   ["/users/new" {:get kcu/new-user}]
   ["/users/:id/show" {:get kcu/show }]
   ["/users/:id/edit" {:get kcu/edit }]
   ["/users/:id/delete" {:get kcu/delete-question
                         :delete kcu/delete}]
   ["/users/:id/patch" {:post kcu/patch}]])

