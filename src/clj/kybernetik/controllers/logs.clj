(ns kybernetik.controllers.logs
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [kybernetik.utils :as u]
   [ring.util.response :as rur]))

(defn- get-log-attrs [{:keys [email timesheet-id]}]
  (let [projects (db/list-user-projects [:user/email email])
        timesheets (db/list-timesheets [:user/email email])]
    (merge
     {:log/date {:type :date
                 :placeholder "Date"}
      :log/project {:type :selection
                    :placeholder (mapv
                                  (fn [{:keys [:db/id :project/ref]}]
                                    [id ref])
                                  projects)}
      :log/effort {:type :float
                   :placeholder "Effort"}
      :log/note {:type :string
                 :placeholder "Note"}}
     (when-not timesheet-id
       {:log/timesheet {:type :selection
                        :placeholder (mapv
                                      (fn [{:keys [:db/id :timesheet/start-date]}]
                                        [id (-> start-date u/date->month-str)])
                                      timesheets)}})
     (when timesheet-id
       (let [{:keys [:timesheet/start-date :timesheet/end-date]} (db/get-timesheet timesheet-id)]
         {:log/date {:type :date
                     :placeholder "Date"
                     :min (u/date->str start-date)
                     :max (u/date->str end-date)}})))))

(defn index [{{:keys [identity]} :session :keys [flash] :as request}]
  (let [attrs [:db/id
               :log/timesheet
               :log/project
               :log/date
               :log/note
               :log/effort]
        rows (mapv (fn [log]
                     (mapv (fn [a]
                             (case a
                               :log/project (let [{:keys [:project/ref :db/id]} (:log/project log)]
                                              [(str "/projects/" id "/show") ref])
                               :log/date (-> log :log/date u/date->str)
                               :log/timesheet (if-let [{:keys [:timesheet/start-date :db/id]} (:timesheet/_logs log)]
                                                [(str "/timesheets/" id "/show") (-> start-date u/date->month-str)]
                                                "-")
                               (a log))) attrs))
                   (db/list-logs [:user/email identity]))]
    (layout/render
     request
     (layout/index {:model "log"
                    :attrs attrs
                    :rows  rows})
     (merge
      {:title "Listing Logs"
       :page "logs"}
      (when flash
        {:message {:text flash
                   :type :info}})))))

(defn create [{{:keys [identity]} :session
               query :query-params
               {:keys [project note date effort timesheet-id]} :params}]
  (let [tid-raw (get query "tid")
        tid (when tid-raw (Integer/parseInt tid-raw))
        new-log {:log/project (Integer/parseInt project)
                 :log/date (if date
                             (u/str->date date)
                             (java.util.Date.))
                 :log/note note
                 :log/timesheet (if tid
                                  tid
                                  (Integer/parseInt timesheet-id))
                 :log/user [:user/email identity]
                 :log/effort (Float/parseFloat effort)}]
    (try
      (db/create-log new-log)
      (assoc (rur/redirect (if tid
                             (str "/timesheets/" tid "/show") 
                             "/logs")) :flash "Log sucessfully created.")
      (catch Exception _
        (assoc (rur/redirect (str "/logs/new")) :flash "Log could not be created.")))))

(defn new-log [{{:keys [identity]} :session
                query :query-params
                :as request}]
  (let [tid-raw (get query "tid")
        tid (when tid-raw (Integer/parseInt tid-raw))
        timesheet-date (when tid (-> (db/touch-timesheet tid) :timesheet/start-date u/date->month-str))]
    (layout/render
     request
     (layout/new {:attrs (get-log-attrs {:email identity :timesheet-id tid})
                  :model "log"
                  :title-postfix (str " for Timesheet " timesheet-date)
                  :submit-params {:tid tid}})
     {:title (if tid
               (str "New Log for Timesheet " timesheet-date)
               "New Log")
      :page "logs"})))

(defn- get-log-entity [id]
  (-> (Integer/parseInt id)
      db/get-log
      (update-in [:log/user] (fn [{:keys [:db/id :user/ref]}] [(str "/users/" id "/show") ref]))
      (update-in [:log/project] (fn [{:keys [:db/id :project/ref]}] [(str "/projects/" id "/show") ref]))
      (update-in [:log/date] u/date->str)))

(defn show [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/show
    {:model "log"
     :id id
     :entity (get-log-entity id)})
   (merge
    {:title "Show Log"
     :page "logs"}
    (when flash
      {:message {:text flash
                 :type :info}}))))

(defn edit [{{:keys [identity]} :session
             {:keys [id]} :path-params
             :as request}]
  (layout/render
   request
   (layout/edit {:attrs (get-log-attrs identity)
                 :values (get-log-entity id)
                 :id id
                 :model "log"})
   {:title "Edit Log"
    :page "logs"}))

(defn delete-question [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/delete
    {:model "log"
     :id id
     :entity (get-log-entity id)})
   (merge
    {:title "Delete Log"
     :page "logs"}
    {:message {:text "Do you really want to delete the following Log?"
               :type :error}})))

(defn delete [{{:keys [id]} :path-params}]
  (try
    (db/delete (Integer/parseInt id))
    (assoc (rur/redirect "/logs") :flash "Log sucessfully deleted.")
    (catch Exception _
      (assoc (rur/redirect "/logs") :flash "Log could not be deleted."))))

(defn patch [{{:keys [tid id]} :path-params
              {:keys [identity]} :session
              {:keys [_method project date note effort timesheet]} :params
              :as request}]
  (if (= "delete" _method)
    (delete request)
    (let [updated-log (merge {:db/id (Integer/parseInt id)}
                             (when timesheet
                               {:log/timesheet (Integer/parseInt timesheet)})
                             (when project
                               {:log/project (Integer/parseInt project)})
                             (when date
                               {:log/date (u/str->date date)})
                             (when note
                               {:log/note note})
                             (when effort
                               {:log/effort (Float/parseFloat effort)}))]
      (try
        (db/update-log updated-log)
        (assoc (rur/redirect (str "/logs")) :flash "Log sucessfully updated.")
        (catch Exception e
          (assoc (rur/redirect "/logs") :flash "Log could not be updated."))))))



