(ns kybernetik.controllers.timesheets
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [kybernetik.utils :as u]
   [ring.util.response :as rur]))

(def months ["January"
             "February"
             "March"
             "April"
             "May"
             "June"
             "July"
             "August"
             "September"
             "October"
             "November"
             "December"])

(defn- get-timesheet-attrs [user-id]
  (let [users (db/list-users)
        current-timesheet-months (->> {:user-id user-id}
                                      db/get-current-year-user-timesheet-dates
                                      (map #(get months (.getMonth %)))
                                      (into #{}))]
    {:timesheet/year {:type :value
                      :value (str (+ 1900 (.getYear (java.util.Date.))))}
     :timesheet/month {:type :selection
                       :placeholder (->>  months
                                          (map-indexed (fn [idx itm]
                                                         [(-> idx inc str) itm]))
                                          (remove (fn [[idx itm]] (current-timesheet-months itm)))
                                          vec)}
     :timesheet/supervisor {:type :selection
                            :placeholder (mapv
                                          (fn [{:keys [:db/id :user/firstname :user/lastname]}]
                                            [id (str firstname " " lastname)])
                                          users)}}))

(defn new-timesheet [{{:keys [identity]} :session :as request}]
  (layout/render
   request
   (layout/new {:attrs (get-timesheet-attrs [:user/email identity])
                :model "timesheet"})
   {:title "New Timesheet"
    :page "timesheet"}))

(defn- format-month [m]
  (if (< m 10)
    (str "0" m)
    (str m)))

(defn create [{{:keys [identity]} :session {:keys [supervisor month]} :params}]
  (let [parsed-month (Integer/parseInt month)
        year (+ 1900 (.getYear (java.util.Date.)))
        start-date (u/str->date (str year "-" (format-month parsed-month) "-01"))
        end-date (u/str->date (str year "-" (-> parsed-month inc format-month) "-01"))
        _ (.setTime end-date (- (.getTime end-date) 1000)) ;; dirty hack for handling Date arithmetic!
        new-timesheet {:timesheet/supervisor (Integer/parseInt supervisor)
                       :timesheet/user [:user/email identity]
                       :timesheet/start-date start-date
                       :timesheet/end-date end-date}]
    (try
      (db/create-timesheet new-timesheet)
      (assoc (rur/redirect "/timesheets") :flash "Timesheet sucessfully created.")
      (catch Exception _
        (assoc (rur/redirect (str "/timesheet/new")) :flash "Timesheet could not be created.")))))

(defn index [{{:keys [identity]} :session :keys [flash] :as request}]
  (let [user-id [:user/email identity]
        attrs [:db/id
               :timesheet/date
               :timesheet/supervisor
               :timesheet/effort-sum
               :timesheet/approved?
               :timesheet/submitted?]
        rows (mapv (fn [{:keys [:timesheet/supervisor :timesheet/start-date :timesheet/logs] :as log}]
                     (mapv (fn [a]
                             (case a
                               :timesheet/supervisor (let [{:keys [:user/ref :db/id]} supervisor]
                                                       [(str "/users/" id "/show") ref])
                               :timesheet/date (-> start-date u/date->month-str)
                               :timesheet/effort-sum (->> logs
                                                          (map :log/effort)
                                                          (reduce +))
                               (a log))) attrs))
                   (db/list-timesheets {:user-id user-id}))]
    (layout/render
     request
     (layout/index {:model "timesheet"
                    :attrs attrs
                    :rows  rows
                    :buttons {:submit {:icon :upload
                                       :type :success}
                              :edit {:icon :square-edit-outline}
                              :delete {:icon :delete
                                       :type :danger}}})
     (merge
      {:title "Listing Timesheets"
       :page "timesheets"}
      (when flash
        {:message {:text flash
                   :type :info}})))))

(defn- get-timesheet-entity [id]
  (let [{:keys [:timesheet/logs :timesheet/start-date] :as timesheet} (-> (Integer/parseInt id) db/get-timesheet)]
    (-> timesheet
        (update :timesheet/supervisor (fn [{:keys [:user/ref :db/id]}] [(str "/users/" id "/show") ref]))
        (update :timesheet/start-date u/date->month-str)
        (dissoc :timesheet/end-date)
        (dissoc :timesheet/logs)
        (assoc :timesheet/year (str (+ 1900 (.getYear start-date))))
        (assoc :timesheet/month (get months (.getMonth start-date)))
        (assoc :timesheet/effort-sum (->> logs
                                          (map :log/effort)
                                          (reduce +))))))

(defn edit [{{:keys [id]} :path-params
             {:keys [identity]} :session
             :as request}]
  (layout/render
   request
   (layout/edit {:attrs (get-timesheet-attrs {:user-id identity})
                 :values (get-timesheet-entity id)
                 :disabled #{:timesheet/start-date :timesheet/year :timesheet/month}
                 :id id
                 :model "timesheet"})
   {:title "Edit Timesheet"
    :page "timesheet"}))

(defn show [{{:keys [id]} :path-params
             {:keys [identity]} :session
             flash :flash
             :as request}]
  (let [log-attrs [:db/id
                   :log/project
                   :log/date
                   :log/note
                   :log/effort]
        log-rows (mapv (fn [log]
                         (mapv (fn [a]
                                 (case a
                                   :log/project (let [{:keys [:project/ref :db/id]} (:log/project log)]
                                                  [(str "/projects/" id "/show") ref])
                                   :log/date (-> log :log/date u/date->str)
                                   (a log))) log-attrs))
                       (sort-by :log/date (db/list-logs {:user-id [:user/email identity] :timesheet-id (Integer/parseInt id)})))]
    (layout/render
     request
     (layout/container
      (layout/show
       {:model "timesheet"
        :id id
        :entity (get-timesheet-entity id)
        :actions {:edit {}
                  :submit {}
                  :delete {:type :danger}}})
      (layout/index {:model "log"
                     :attrs log-attrs
                     :rows  log-rows
                     :actions {:new {:tid id}}}))
     (merge
      {:title "Show Timesheet"
       :page "timesheets"}
      (when flash
        {:message {:text flash
                   :type :info}})))))

(defn submit-question [{{:keys [id]} :path-params
                        {:keys [identity]} :session
                        :as request}]
  (let [log-attrs [:db/id
                   :log/project
                   :log/date
                   :log/note
                   :log/effort]
        log-rows (mapv (fn [log]
                         (mapv (fn [a]
                                 (case a
                                   :log/project (let [{:keys [:project/ref :db/id]} (:log/project log)]
                                                  [(str "/projects/" id "/show") ref])
                                   :log/date (-> log :log/date u/date->str)
                                   (a log))) log-attrs))
                       (sort-by :log/date (db/list-logs {:user-id [:user/email identity] :timesheet-id (Integer/parseInt id)})))]
    (layout/render
     request
     (layout/container
      (layout/question
       {:model "timesheet"
        :action :submit
        :value "submit"
        :type :info
        :id id
        :entity (get-timesheet-entity id)})
      (layout/index {:model "log"
                     :attrs log-attrs
                     :rows  log-rows
                     :actions {:new {:tid id}}}))
     (merge
      {:title "Submit Timesheet"
       :page "timesheets"}
      {:message {:text "Do you really want to submit the following Timesheet?"
                 :type :info}}))))

(defn submit [{{:keys [id]} :path-params}]
  (try
    (db/submit-timesheet (Integer/parseInt id))
    (assoc (rur/redirect (str "/timesheets/" id "/show")) :flash "Timesheet sucessfully submitted. Your supervisor will have to approve it.")
    (catch Exception _
      (assoc (rur/redirect (str "/timesheet/" id "/show")) :flash "Timesheet could not be submitted."))))



(defn delete-question [{{:keys [id]} :path-params :as request}]
  (layout/render
   request
   (layout/delete
    {:model "timesheet"
     :id id
     :entity (get-timesheet-entity id)})
   (merge
    {:title "Delete Timesheet"
     :page "timesheets"}
    {:message {:text "Do you really want to delete the following Timesheet?"
               :type :error}})))

(defn delete [{{:keys [id]} :path-params}]
  (try
    (db/delete (Integer/parseInt id))
    (assoc (rur/redirect "/timesheets") :flash "Timesheet sucessfully deleted.")
    (catch Exception _
      (assoc (rur/redirect "/timesheets") :flash "Timesheet could not be deleted."))))

(defn patch [{{:keys [id]} :path-params
              {:keys [_method supervisor]} :params
              :as request}]
  (case _method
    "delete" (delete request)
    (let [updated-timesheet (merge {:db/id (Integer/parseInt id)}
                                   (when supervisor
                                     {:timesheet/supervisor (Integer/parseInt supervisor)}))]
      (try
        (db/update-timesheet updated-timesheet)
        (assoc (rur/redirect (str "/timesheets")) :flash "Timesheet sucessfully updated.")
        (catch Exception _
          (assoc (rur/redirect "/timesheets") :flash "Timesheet could not be updated."))))))



