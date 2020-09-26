(ns kybernetik.routes.home
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.controllers.users :as kcu]
   [kybernetik.controllers.projects :as kcp]
   [kybernetik.controllers.welcome :as kcw]
   [clojure.java.io :as io]
   [kybernetik.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn public-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}

   ["/" {:get kcw/index}]

   ["/sign-in" {:get kcu/sign-in
                :post kcu/sign-in!}]

   ["/sign-out" {:get kcu/sign-out!}]])

(defn routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats
                 middleware/wrap-restricted]}

   ["/users" {:get kcu/index
              :post kcu/create}]
   ["/users/new" {:get kcu/new-user}]
   ["/users/:id/show" {:get kcu/show }]
   ["/users/:id/edit" {:get kcu/edit }]
   ["/users/:id/delete" {:get kcu/delete-question
                         :delete kcu/delete}]
   ["/users/:id/patch" {:post kcu/patch}]

   ["/projects" {:get kcp/index
                 :post kcp/create}]
   ["/projects/new" {:get kcp/new-project}]
   ["/projects/:id/show" {:get kcp/show }]
   ["/projects/:id/edit" {:get kcp/edit }]
   ["/projects/:id/delete" {:get kcp/delete-question
                            :delete kcp/delete}]
   ["/projects/:id/patch" {:post kcp/patch}]])


