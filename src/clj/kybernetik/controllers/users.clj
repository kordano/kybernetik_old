(ns kybernetik.controllers.users
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [ring.util.response :as rur]))

(defn index [{:keys [flash] :as request}]
  (let [attrs [:db/id
               :user/ref
               :user/name
               :user/firstname
               :user/lastname
               :user/role]
        rows (mapv (fn [user]
                     (mapv (fn [a] (if (= :user/role a)
                                     (-> user :user/role :db/ident)
                                     (a user))) attrs))
                   (db/list-users))]
    (layout/render
     request
     (layout/index {:model "user"
                    :attrs attrs
                    :rows  rows})
     (merge
      {:title "Listing Users"
       :page "users"}
      (when flash
        {:message {:text flash
                   :type :info}})))))

(defn create [{:keys [form-params params] :as request}]
  (let [new-user {:user/name (:name params)
                  :user/firstname (:firstname params)
                  :user/lastname (:lastname params)
                  :user/role (keyword "role" (:role params))}]
    (try
      (do
        (let [id (db/create-user new-user)]
          (assoc (rur/redirect (str "/users/" id "/show")) :flash "User sucessfully created.")))
      (catch Exception e
        (assoc (rur/redirect (str "/users/new" )) :flash "User could not be created.")))))



(defn new-user [request]
  (layout/render
   request
   (layout/new {:attrs {:user/name {:type :text
                                    :placeholder "LDAP username"}
                        :user/firstname {:type :text
                                         :placeholder "Firstname"}
                        :user/lastname {:type :text
                                        :placeholder "Lastname"}
                        :user/role {:type :selection
                                    :placeholder [[:role/admin :role/admin]
                                                  [:role/manager :role/manager]
                                                  [:role/employee :role/employee]
                                                  [:role/contractor :role/contractor]]}}
                :model "user"})
   {:title "New User"
    :page "users"}))

(defn edit [{{:keys [id]} :path-params :as request}]
  (let [user (db/get-user (Integer/parseInt id))]
    (layout/render
     request
     (layout/edit {:attrs {:user/name {:type :text
                                      :placeholder "LDAP username"}
                          :user/firstname {:type :text
                                           :placeholder "Firstname"}
                          :user/lastname {:type :text
                                          :placeholder "Lastname"}
                          :user/role {:type :selection
                                      :placeholder [[:role/admin :role/admin]
                                                    [:role/manager :role/manager]
                                                    [:role/employee :role/employee]
                                                    [:role/contractor :role/contractor]]}}
                   :values (update user :user/role :db/ident)
                   :id id
                  :model "user"})
     {:title "Edit User"
      :page "users"})))

(defn show [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/show
    {:model "user"
     :entity (-> (db/get-user (Integer/parseInt id))
                 (update-in [:user/role] :db/ident))})
   (merge
    {:title "Show User"
     :page (str "users/" id "/show")}
    (when flash
      {:message {:text flash
                 :type :info}}))))


(defn delete [{{:keys [id]} :path-params :as request}]
  (try
    (do
      (db/delete (Integer/parseInt id))
      (assoc (rur/redirect "/users") :flash "User sucessfully deleted."))
    (catch Exception e
      (assoc (rur/redirect "/users") :flash "User could not be deleted."))))

(defn delete-question [{{:keys [id]} :path-params flash :flash :as request}]
  (layout/render
   request
   (layout/delete
    {:model "user"
     :id id
     :entity (-> (db/get-user (Integer/parseInt id))
                 (update-in [:user/role] :db/ident))})
   (merge
    {:title "Delete User"
     :page "users"}
    {:message {:text "Do you really want to delete the following User?"
               :type :error}})))


(defn patch [{{:keys [id]} :path-params
              {:keys [_method name firstname lastname role]} :params
              :as request}]
  (if (= "delete" _method)
    (delete request)
    (let [updated-user (merge {:db/id (Integer/parseInt id)}
                              (when name {:user/name name})
                              (when firstname {:user/firstname firstname})
                              (when lastname {:user/lastname lastname})
                              (when role {:user/role (keyword "role" role)}))]
      (try
        (do
          (db/update-user updated-user)
          (assoc (rur/redirect (str "/users/" id "/show")) :flash "User sucessfully updated."))
        (catch Exception e
          (assoc (rur/redirect "/users/") :flash "User could not be updated."))))))
