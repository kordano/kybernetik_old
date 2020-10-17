(ns kybernetik.controllers.welcome
  (:require
   [kybernetik.layout :as layout]
   [ring.util.response :as rur]
   [kybernetik.db.core :as db]))

(defn index [{{:keys [identity]} :session
              :as request}]
  (if (boolean identity)
    (let [{:keys [:timesheet/logs :db/id]} (db/get-current-timesheet [:user/email identity])]
      (layout/render
       request
       (layout/welcome {:current-hours (reduce + (map :log/effort logs))
                        :current-project-count (->> logs
                                                    (map :log/project)
                                                    (into #{})
                                                    count)
                        :timesheet-id id
                        :target-hours 160})))
    (rur/redirect "/sign-in")))

