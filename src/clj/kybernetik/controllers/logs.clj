(ns kybernetik.controllers.logs
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [kybernetik.utils :as u]
   [ring.util.response :as rur]))

(defn- get-log-attrs [identity]
  (let [projects (db/list-user-projects [:user/email identity])]
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
                :placeholder "Note"}}))

(defn index [{:keys [flash] :as request}]
  (let [attrs [:db/id
               :log/project
               :log/user
               :log/date
               :log/note
               :log/effort]
        rows (mapv (fn [log]
                     (mapv (fn [a]
                             (case a
                               :log/user (let [{:keys [:user/ref :db/id]} (:log/user log)]
                                           [(str "/users/" id "/show") ref])
                               :log/project (let [{:keys [:project/ref :db/id]} (:log/project log)]
                                              [(str "/projects/" id "/show") ref])
                               :log/date (-> log :log/date u/date->str)
                               (a log))) attrs))
                   (db/list-logs))]
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

(defn create [{{:keys [identity]} :session {:keys [project note date effort]} :params :as request}]
  (let [new-log {:log/project (Integer/parseInt project)
                 :log/date (if date
                             (u/str->date date)
                             (java.util.Date.))
                 :log/note note
                 :log/user [:user/email identity]
                 :log/effort (Float/parseFloat effort)}]
    (try
      (do
        (let [id (db/create-log new-log)]
          (assoc (rur/redirect "/logs") :flash "Log sucessfully created.")))
      (catch Exception e
        (assoc (rur/redirect (str "/logs/new")) :flash "Log could not be created.")))))

(defn new-log [{{:keys [identity]} :session :as request}]
  (layout/render
   request
   (layout/new {:attrs (get-log-attrs identity)
                :model "log"})
   {:title "New Log"
    :page "logs"}))

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
  (let [log (db/get-log (Integer/parseInt id))]
    (layout/render
     request
     (layout/edit {:attrs (get-log-attrs identity)
                   :values (get-log-entity id)
                   :id id
                   :model "log"})
     {:title "Edit Log"
      :page "logs"})))

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

(defn delete [{{:keys [id]} :path-params :as request}]
  (try
    (do
      (db/delete (Integer/parseInt id))
      (assoc (rur/redirect "/logs") :flash "Log sucessfully deleted."))
    (catch Exception e
      (assoc (rur/redirect "/logs") :flash "Log could not be deleted."))))

(defn patch [{{:keys [id]} :path-params
              {:keys [identity]} :session
              {:keys [_method project date note effort]} :params
              :as request}]
  (if (= "delete" _method)
    (delete request)
    (let [updated-log (merge {:db/id (Integer/parseInt id)}
                             (when project
                               {:log/project (Integer/parseInt project)})
                             (when date
                               {:log/date (u/str->date date)})
                             (when note
                               {:log/note note})
                             (when effort
                               {:log/effort (Float/parseFloat effort)}))]
      (try
        (do
          (db/update-log updated-log)
          (assoc (rur/redirect (str "/logs")) :flash "Log sucessfully updated."))
        (catch Exception e
          (assoc (rur/redirect "/logs") :flash "Log could not be updated."))))))



