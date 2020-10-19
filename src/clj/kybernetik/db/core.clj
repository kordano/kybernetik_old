(ns kybernetik.db.core
  (:require [datahike.api :as d]
            [buddy.hashers :as hashers]
            [kybernetik.config :refer [env]]
            [mount.core :as mount]
            [kybernetik.utils :as u]
            [clojure.string :as s]
            [clojure.set :as st]))

(def manager-roles #{:role/admin :role/manager})

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

(defn user-entity [id]
  (d/entity @conn id))

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
  (d/q '[:find ?e
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

(defn user-is-manager? [id]
  (-> id user-entity :user/role :db/id user-entity :db/ident manager-roles boolean))

(defn get-log [id]
  (d/pull @conn '[* {:log/user [:db/id :user/ref :user/firstname :user/lastname]
                     :timesheet/_logs [:db/id :timesheet/start-date]
                     :log/project [:db/id :project/ref :project/title]}] id))

(defn create-log [{:keys [:log/timesheet] :as new-log}]
  (let [{:keys [tempids]} (d/transact conn [(-> new-log
                                                (dissoc :log/timesheet)
                                                (assoc :db/id -1))
                                            {:db/id timesheet
                                             :timesheet/logs -1}])]
    (get tempids -1)))

(defn update-log [{:keys [:db/id :log/timesheet] :as updated-log}]
  (d/transact conn (if timesheet
                     [(dissoc updated-log :log/timesheet)
                      {:db/id timesheet
                       :timesheet/logs id}]
                     [updated-log])))

(def log-pull-pattern '[* {:log/user [:db/id :user/ref]
                           :timesheet/_logs [:db/id :timesheet/start-date]
                           :log/project [:db/id :project/ref]}])

(def log-ts-pull-pattern '[* {:log/user [:db/id :user/ref]
                              :log/project [:db/id :project/ref]}])

(def log-user-pull-pattern '[:db/id :log/date :log/effort :log/note {:log/project [:db/id :project/ref]
                                                                     :timesheet/_logs [:db/id :timesheet/start-date]}])

(def log-user-ts-pull-pattern '[:db/id :log/date :log/effort :log/note {:log/project [:db/id :project/ref]}])

(defn build-log-query
  ([db]
   (build-log-query db {}))
  ([db {:keys [user-id timesheet-id]}]
   (cond-> {:query '{:find [[(pull ?l ?pull-pattern) ...]]
                     :in [$ ?pull-pattern]
                     :where
                     []}
            :args [db]}
     (and user-id
          (nil? timesheet-id)) (-> (update-in [:args] conj log-user-pull-pattern)
                                   (update-in [:args] conj user-id)
                                   (update-in [:query :in] conj '?u)
                                   (update-in [:query :where] conj '[?l :log/user ?u]))
     (and (nil? user-id)
          (nil? timesheet-id)) (update-in [:args] conj log-pull-pattern)
     (and (nil? user-id)
          timesheet-id) (-> (update-in [:args] conj log-ts-pull-pattern)
                            (update-in [:args] conj timesheet-id)
                            (update-in [:query :in] conj '?t)
                            (update-in [:query :where] conj '[?t :timesheet/logs ?l]))
     (and user-id
          timesheet-id) (-> (update-in [:args] conj log-user-ts-pull-pattern)
                            (update-in [:args] conj user-id)
                            (update-in [:query :in] conj '?u)
                            (update-in [:query :where] conj '[?l :log/user ?u])
                            (update-in [:args] conj timesheet-id)
                            (update-in [:query :in] conj '?t)
                            (update-in [:query :where] conj '[?t :timesheet/logs ?l]))
     true (update-in [:query :where] conj '[?l :log/project _]))))

(defn list-logs
  ([]
   (list-logs nil))
  ([{:keys [user-id timesheet-id]}]
   (-> @conn
       (build-log-query {:user-id user-id
                         :timesheet-id timesheet-id})
       d/q)))

(defn create-timesheet [new-timesheet]
  (let [tx [(assoc new-timesheet
                   :db/id -1
                   :timesheet/approved? false
                   :timesheet/submitted? false)]
        {:keys [tempids]} (d/transact conn tx)]
    (get tempids -1)))

(def timesheet-user-pull-pattern '[:db/id :timesheet/start-date :timesheet/end-date :timesheet/approved? :timesheet/submitted? {:timesheet/supervisor [:db/id :user/ref]
                                                                                                                                :timesheet/logs [:db/id :log/effort]}])

(def timesheet-pull-pattern '[:db/id :timesheet/start-date :timesheet/end-date :timesheet/approved? :timesheet/submitted? {:timesheet/user [:db/id :user/ref]
                                                                                                                           :timesheet/logs [:db/id :log/effort]}])

(defn build-timesheet-query
  ([db]
   (build-timesheet-query db {}))
  ([db {:keys [user-id approved?]}]
   (cond-> {:query '{:find [[(pull ?t ?pull-pattern) ...]]
                     :in [$ ?pull-pattern]
                     :where
                     []}
            :args [db]}
     user-id (-> (update-in [:args] conj timesheet-user-pull-pattern)
                 (update-in [:query :in] conj '?u)
                 (update-in [:args] conj user-id)
                 (update-in [:query :where] conj '[?t :timesheet/user ?u]))
     (nil? user-id) (update-in [:args] conj timesheet-pull-pattern)
     (nil? approved?) (update-in [:query :where] conj  '[?t :timesheet/approved? false])
     (not (nil? approved?)) (-> (update-in [:args] conj approved?)
                                (update-in [:query :in] conj '?ap)
                                (update-in [:query :where] conj '[?t :timesheet/approved? ?ap]))
     true (update-in [:query :where] conj '[?t :timesheet/start-date _]))))

(defn list-timesheets
  ([]
   (list-timesheets {}))
  ([{:keys [user-id approved?]}]
   (let [db @conn]
     (->> (build-timesheet-query db {:user-id user-id
                                     :approved? approved?})
          d/q))))

(defn get-timesheet [id]
  (d/pull @conn timesheet-user-pull-pattern id))

(defn update-timesheet [updated-timesheet]
  (d/transact conn [updated-timesheet]))

(defn get-current-timesheet [id]
  (d/q {:query '{:find [(pull ?t ?pull-pattern) .]
                 :in [$ ?u ?d ?pull-pattern]
                 :where [[?t :timesheet/user ?u]
                         [?t :timesheet/start-date ?sd]
                         [?t :timesheet/end-date ?ed]
                         [(<= ?sd ?d)]
                         [(< ?d ?ed)]]}
        :args [@conn id (java.util.Date.) timesheet-user-pull-pattern]}))

(defn touch-timesheet [id]
  (d/entity @conn id))

(defn submit-timesheet [id]
  (d/transact conn [{:db/id id
                     :timesheet/submitted? true}]))

(defn get-current-year-user-timesheet-dates [{:keys [user-id]}]
  (let [current-date (java.util.Date.)
        start-date (u/precise-str->date (str (+ 1900 (.getYear current-date)) "-01-01-00:00:00"))
        end-date (u/precise-str->date (str (+ 1900 (.getYear current-date)) "-12-31-23:59:59"))
        query (cond-> {:query '{:find [[?d ...]]
                                :in [$ ?sd ?ed]
                                :where [[?t :timesheet/start-date ?d]
                                        [(<= ?sd ?d)]
                                        [(< ?d ?ed)]]}
                       :args [@conn start-date end-date]}
                user-id (-> (update-in [:args] conj user-id)
                            (update-in [:query :in] conj '?u)
                            (update-in [:query :where] conj '[?t :timesheet/user ?u])))]
    (d/q query)))

(defn create-vaction [new-vacation]
  (let [{:keys [tempids]} (d/transact conn [(-> (assoc new-vacation :db/id -1 :vacation/approved? false)
                                                (st/rename-keys {:vacation/user :user/_vacations}))
])]
    (get tempids -1)))

(def vacation-user-pull-pattern
  '[:db/id :vacation/date :vacation/approved? :vacation/hours])

(def vacation-pull-pattern
  (conj vacation-user-pull-pattern {:user/_vacations [:db/id :user/ref]}))

(defn list-vacations
  ([]
   (list-vacations nil))
  ([{:keys [user-id]}]
   (let [query (-> {:query '{:find [[(pull ?v ?pull-pattern) ...]]
                             :in [$ ?pull-pattern]
                             :where [[?v :vacation/date _]]}
                    :args [@conn]}
                   (cond->
                    (nil? user-id) (-> (update-in [:args] conj vacation-pull-pattern))
                    (not (nil? user-id)) (-> (update-in [:args] conj vacation-user-pull-pattern)
                                             (update-in [:args] conj user-id)
                                             (update-in [:query :in] conj '?u)
                                             (update-in [:query :where] conj '[?u :user/vacations ?v]))))]
     (d/q query))))

(defn get-vacation [id]
  (d/pull @conn vacation-pull-pattern id))

(defn update-vacation [updated-vacation]
  (d/transact conn [updated-vacation]))

(comment

  (def db @conn)
  
  (def user-id 39)

  (list-users)
  
  (create-vaction {:vacation/date (java.util.Date.)
                   :vacation/hours 1.0
                   :vacation/user 40})
  
  (get-current-year-user-timesheet-dates {:user-id 38})
  
  )

