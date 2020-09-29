(ns kybernetik.controllers.projects
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [kybernetik.utils :as u]
   [ring.util.response :as rur]))

(defn index [{:keys [flash] :as request}]
  (let [attrs [:db/id
               :project/ref
               :project/title
               :project/description
               :project/members
               :project/start-date
               :project/end-date
               :project/supervisor]
        rows (mapv (fn [proj]
                     (mapv (fn [a]
                             (case a
                               :project/supervisor (let [{:keys [:user/ref :db/id]} (:project/supervisor proj)]
                                                     [(str "/users/" id "/show") ref])
                               :project/members (mapv (fn [{:keys [:db/id :user/ref]}]
                                                        [(str "/users/" id "/show") ref]) (:project/members proj))
                               :project/start-date (-> proj :project/start-date u/date->str)
                               :project/end-date (-> proj :project/end-date u/date->str)
                               (a proj))) attrs))
                   (db/list-projects))]
    (layout/render
     request
     (layout/index {:model "project"
                    :attrs attrs
                    :rows  rows})
     (merge
      {:title "Listing Projects"
       :page "projects"}
      (when flash
        {:message {:text flash
                   :type :info}})))))


(defn create [{{:keys [title description members supervisor start-date end-date]} :params :as request}]
  (let [new-project {:project/title title
                     :project/description description
                     :project/supervisor (Integer/parseInt supervisor)
                     :project/members (if (vector? members)
                                        (mapv #(Integer/parseInt %) members)
                                        (Integer/parseInt members))
                     :project/start-date (if start-date
                                           (u/str->date start-date)
                                           (java.util.Date.))
                     :project/end-date (if end-date
                                         (u/str->date end-date)
                                         (java.util.Date. 129 11 31 23 59 59))}]
    (try
      (do
        (let [id (db/create-project new-project)]
          (assoc (rur/redirect "/projects") :flash "Project sucessfully created.")))
      (catch Exception e
        (assoc (rur/redirect (str "/projects/new" )) :flash "Projects could not be created.")))))

(defn new-project [request]
  (let [users (db/list-users)]
    (layout/render
     request
     (layout/new {:attrs {:project/title {:type :text
                                          :placeholder "Title"}
                          :project/description {:type :text
                                                :placeholder "Description"}
                          :project/supervisor  {:type :selection
                                                :placeholder (mapv
                                                              (fn [{:keys [:db/id :user/firstname :user/lastname]}]
                                                                [id (str firstname " " lastname)])
                                                              users)}
                          :project/members {:type :multi-selection
                                            :placeholder (mapv
                                                          (fn [{:keys [:db/id :user/firstname :user/lastname]}]
                                                            [id (str firstname " " lastname)])
                                                          users)}
                          :project/start-date {:type :date
                                               :placeholder "Start Date"}
                          :project/end-date {:type :date
                                             :placeholder "End Date"}}
                  :model "project"})
     {:title "New Project"
      :page "projects"})))


(defn show [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/show
    {:model "project"
     :id id
     :entity (-> (db/get-project (Integer/parseInt id))
                 (update-in [:project/supervisor] (fn [{:keys [:db/id :user/ref]}] [(str "/users/" id "/show") ref]))
                 (update-in [:project/members] (fn [mbrs] (mapv (fn [{:keys [:db/id :user/ref]}] [(str "/users/" id "/show") ref]) mbrs)) ))})
   (merge
    {:title "Show Project"
     :page "projects"}
    (when flash
      {:message {:text flash
                 :type :info}}))))

(defn edit [{{:keys [id]} :path-params :as request}]
  (let [proj (db/get-project (Integer/parseInt id))
        users (db/list-users)]
    (layout/render
     request
     (layout/edit {:attrs {:project/title {:type :text
                                           :placeholder "Title"}
                           :project/description {:type :text
                                                 :placeholder "Description"}
                           :project/supervisor  {:type :selection
                                                 :placeholder (mapv
                                                               (fn [{:keys [:db/id :user/firstname :user/lastname]}]
                                                                 [id (str firstname " " lastname)])
                                                               users)}}
                   :values (update proj :project/supervisor (fn [{:keys [:db/id :user/firstname :user/lastname]}]
                                                                 [id (str firstname " " lastname)]))
                   :id id
                   :model "project"})
     {:title "Edit Project"
      :page "projects"})))

(defn delete-question [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/delete
    {:model "project"
     :id id
     :entity (-> (db/get-project (Integer/parseInt id))
                 (update-in [:project/supervisor] (fn [{:keys [:db/id :user/ref]}] [(str "/users/" id "/show") ref]))
                 (update-in [:project/members] (fn [mbrs] (mapv (fn [{:keys [:db/id :user/ref]}] [(str "/users/" id "/show") ref]) mbrs)) ))})
   (merge
    {:title "Delete Project"
     :page "projects"}
    {:message {:text "Do you really want to delete the following Project?"
               :type :error}})))


(defn delete [{{:keys [id]} :path-params :as request}]
  (try
    (do
      (db/delete (Integer/parseInt id))
      (assoc (rur/redirect "/projects") :flash "Project sucessfully deleted."))
    (catch Exception e
      (assoc (rur/redirect "/projects") :flash "Project could not be deleted."))))

(defn patch [{{:keys [id]} :path-params
              {:keys [_method title description members supervisor start-date end-date]} :params
              :as request}]
  (if (= "delete" _method)
    (delete request)
    (let [updated-project (merge {:db/id (Integer/parseInt id)}
                              (when title {:project/title title})
                              (when description {:project/description description})
                              (when members {:project/members (mapv #(Integer/parseInt %) members)})
                              (when supervisor {:project/supervisor (Integer/parseInt supervisor)})
                              (when start-date {:project/start-date start-date})
                              (when end-date {:project/end-date end-date}))]
      (try
        (do
          (db/update-project updated-project)
          (assoc (rur/redirect (str "/projects")) :flash "project sucessfully updated."))
        (catch Exception e
          (assoc (rur/redirect "/projects") :flash "Projects could not be created."))))))



