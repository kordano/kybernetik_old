(ns kybernetik.db.core
  (:require [datahike.api :as d]
            [buddy.hashers :as hashers]
            [kybernetik.config :refer [env]]
            [mount.core :as mount]
            [clojure.string :as s]))

(defn init-db [cfg]
  (let [tx-0 (-> "resources/tx/tx-0.edn" slurp read-string)]
    (d/create-database (assoc cfg :initial-tx tx-0))
    (d/connect cfg)))

(mount/defstate conn
  :start (let [cfg (:db env)]
           (if (d/database-exists? cfg)
             (d/connect cfg)
             (init-db cfg))))

(defn reset-db []
  (mount/stop #'kybernetik.db.core/conn)
  (d/delete-database (:db env))
  (mount/start #'kybernetik.db.core/conn))

(defn- create-ref [strng model-ref]
  (let [letters (s/split strng  #"\s")]
    (loop [reference (if (< (count letters) 2)
                       (-> (str (first strng) (last strng))
                           (s/upper-case))
                       (->> letters
                            (map (comp first s/upper-case))
                            (apply str)))
           i 1]
      (if-not (d/entity @conn [model-ref reference])
        reference
        (recur (if (< (count letters) 2)
                 (-> (str (first strng) (last strng))
                     (s/upper-case)
                     (str i))
                 (str (->> letters
                           (map (comp first s/upper-case))
                           (apply str)) i))
               (inc i))))))

(defn get-user [id]
  (d/pull @conn '[* {:user/role [:db/ident]}] id))

(defn create-user [{:keys [:user/firstname :user/lastname] :as new-user}]
  (let [{:keys [tempids]} (d/transact conn [(-> new-user
                                                (assoc :db/id -1 :user/ref (create-ref (str firstname " " lastname) :user/ref))
                                                (update :user/password hashers/derive))])]
    (get tempids -1)))

(defn update-user [updated-user]
  (d/transact conn [updated-user]))

(defn delete [id]
  (d/transact conn [[:db/retractEntity id]]))

(defn list-users []
  (d/q '[:find [(pull ?e [* {:user/role [:db/ident]}]) ...]
         :where
         [?e :user/name _]]
       @conn))

(defn validate-user [{:keys [email password]}]
  (if-let [user (d/entity @conn [:user/email email])]
    (hashers/check password (:user/password user))
    false))

(defn list-roles []
  (d/q '[:find ?r
         :where
         [?e :db/ident ?]]))

(defn get-project [id]
  (d/pull @conn '[* {:project/supervisor [:db/id :user/ref :user/firstname :user/lastname]
                     :project/members [:db/id :user/ref]}] id))

(defn create-project [{:keys [project/title] :as new-project}]
  (let [{:keys [tempids]} (d/transact conn [(assoc new-project
                                                   :db/id -1
                                                   :project/ref (create-ref title :project/ref))])]
    (get tempids -1)))

(defn update-project [{:keys [project/title] :as updated-project}]
  (d/transact conn [(merge updated-project
                           (when title
                             {:project/ref (create-ref title :project/ref)}))]))

(defn list-projects []
  (d/q '[:find [(pull ?e [* {:project/supervisor [:db/id :user/ref]
                             :project/members [:db/id :user/ref]}]) ...]
         :where
         [?e :project/ref _]]
       @conn))

(defn list-user-projects [user-id]
  (d/q '[:find [(pull ?e [* {:project/supervisor [:db/id :user/ref]
                             :project/members [:db/id :user/ref]}]) ...]
         :in $ ?uid
         :where
         [?e :project/ref _]
         [?e :project/members ?uid]]
       @conn user-id))


(defn get-log [id]
  (d/pull @conn '[* {:log/user [:db/id :user/ref :user/firstname :user/lastname]
                     :log/project [:db/id :project/ref :project/title]}] id))


(defn create-log [new-log]
  (d/transact conn [new-log]))

(defn update-log [updated-log]
  (d/transact conn [updated-log]))

(defn list-logs []
  (d/q '[:find [(pull ?e [* {:log/user [:db/id :user/ref]
                             :log/project [:db/id :project/ref]}]) ...]
         :where
         [?e :log/user _]]
       @conn))
