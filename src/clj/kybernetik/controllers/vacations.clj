(ns kybernetik.controllers.vacations
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [kybernetik.utils :as u]
   [ring.util.response :as rur]))

(defn- get-vacation-attrs []
  {:vacation/start-date {:type :date
                         :value (u/date->str (java.util.Date.))
                         :placeholder "Start Date"}
   :vacation/end-date {:type :date
                       :value (u/date->str (java.util.Date.))
                       :placeholder "End Date"}
   :vacation/hours {:type :number
                    :step "0.5"
                    :min "0.5"
                    :max "8.0"
                    :placeholder "Hours"}})

(defn new-vacation [request]
  (layout/render
   request
   (layout/new
    {:attrs (get-vacation-attrs)
     :model "vacation"})
   {:title "New Vacation"
    :page "vacations"}))

(defn create-vacation [{{:keys [identity]} :session
                        {:keys [hours start-date end-date]} :params}]
  (let [multiple? (and (seq start-date) (seq end-date))
        new-vacation (if multiple?
                       (mapv #() [])
                       (cond-> {:vacation/user [:user/email identity]
                                :vacation/date date}
                         full (merge {:vacation/hours 8.0})
                         (not full) (merge {:vacation/hours hours})))]
    (try
      (db/create-vaction new-vacation)
      (assoc (rur/redirect "/vacations") :flash "Vacation sucessfully created.")
      (catch Exception _
        (assoc (rur/redirect (str "/vacations/vacations")) :flash "Vacation could not be created.")))))