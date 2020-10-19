(ns kybernetik.routes.core
  (:require
   [kybernetik.controllers.users :as kcu]
   [kybernetik.controllers.projects :as kcp]
   [kybernetik.controllers.logs :as kcl]
   [kybernetik.controllers.welcome :as kcw]
   [kybernetik.controllers.timesheets :as kct]
   [kybernetik.controllers.vacations :as kcv]
   [kybernetik.middleware :as middleware]
   [ring.util.response]))

(defn public-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}

   ["/" {:get kcw/index}]

   ["/sign-in" {:get kcu/sign-in
                :post kcu/sign-in!}]

   ["/sign-out" {:get kcu/sign-out!}]])

(def project-routes
  [["/projects" {:get kcp/index
                 :post kcp/create}]
   ["/projects/new" {:get kcp/new-project}]
   ["/projects/:id/edit" {:get kcp/edit}]
   ["/projects/:id/delete" {:get kcp/delete-question
                            :delete kcp/delete}]
   ["/projects/:id/patch" {:post kcp/patch}]])

(def user-routes
  [["/users" {:get kcu/index
              :post kcu/create}]
   ["/users/new" {:get kcu/new-user}]
   ["/users/:id/edit" {:get kcu/edit}]
   ["/users/:id/delete" {:get kcu/delete-question
                         :delete kcu/delete}]
   ["/users/:id/patch" {:post kcu/patch}]])

(def log-routes
  [["/logs" {:post kcl/create}]
   ["/logs/new" {:get {:query-params []
                       :handler kcl/new-log}}]
   ["/logs/:id/show" {:get kcl/show}]
   ["/logs/:id/edit" {:get kcl/edit}]
   ["/logs/:id/delete" {:get kcl/delete-question
                        :delete kcl/delete}]
   ["/logs/:id/patch" {:post kcl/patch}]])

(def show-routes
  [["/projects/:id/show" {:get kcp/show}]
   ["/users/:id/show" {:get kcu/show}]])

(def timesheet-routes
  [["/timesheets" {:post kct/create
                   :get kct/index}]
   ["/timesheets/new" {:get kct/new-timesheet}]
   ["/timesheets/:id/delete" {:get kct/delete-question
                              :delete kct/delete}]
   ["/timesheets/:id/edit" {:get kct/edit}]
   ["/timesheets/:id/patch" {:post kct/patch}]
   ["/timesheets/:id/show" {:get kct/show}]
   ["/timesheets/:id/submit" {:get kct/submit-question
                              :post kct/submit}]])

(def vacation-routes
  [["/vacations"  {:post kcv/create-vacation}]
   ["/vacations/new" {:get kcv/new-vacation}]])

(defn manager-routes []
  (-> [""
       {:middleware [middleware/wrap-csrf
                     middleware/wrap-formats
                     middleware/wrap-manager]}]
      (concat
       project-routes
       user-routes)
      vec))

(defn routes []
  (-> [""
       {:middleware [middleware/wrap-csrf
                     middleware/wrap-formats
                     middleware/wrap-restricted]}]
      (concat
       log-routes
       show-routes
       timesheet-routes
       vacation-routes)
      vec))

