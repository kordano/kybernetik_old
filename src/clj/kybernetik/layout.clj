(ns kybernetik.layout
  (:require
   [clojure.java.io]
   [selmer.parser :as parser]
   [selmer.filters :as filters]
   [hiccup.page :as hp]
   [clojure.string :as s]
   [kybernetik.db.core :as db]
   [markdown.core :refer [md-to-html-string]]
   [ring.util.http-response :refer [content-type ok]]
   [ring.util.anti-forgery :refer [anti-forgery-field]]
   [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(parser/set-resource-path!  (clojure.java.io/resource "html"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

(defn header [{:keys [title]}]
  [:head
   [:title (or title "Kybernetik")]
   (hp/include-css "/assets/bulma/css/bulma.min.css"
                   "/css/mdi/css/materialdesignicons.min.css"
                   "/css/screen.css")])

(def footer
  [:script {:type "text/javascript"}
   "(function() {
        var burger = document.querySelector('.burger');
        var nav = document.querySelector('#'+burger.dataset.target);
        burger.addEventListener('click', function(){
          burger.classList.toggle('is-active');
          nav.classList.toggle('is-active');
        });
      })();"])

(defn navlink [page model]
  [:a {:href (str "/" model "s")
       :class (str "navbar-item" (if (= page (str model "s")) " is-active" ""))}
   (str (s/capitalize model) "s")])

(defn navbar [{:keys [page signed-in? identity]}]
  (let [is-manager? (if signed-in?
                      (db/user-is-manager? [:user/email identity])
                      false)]
    [:nav.navbar.is-light
     [:div.container
      [:div.navbar-brand
       [:a.navbar-item {:href "/"} "kybernetik"]]
      [:div.navbar-menu
       (when signed-in?
         [:div.navbar-start
          (navlink page "timesheet")
          (when is-manager?
            (navlink page "project"))
          (when is-manager?
            (navlink page "user"))])
       [:div.navbar-end
        (when signed-in?
          [:p.navbar-item.has-text-grey.is-size-7 "Signed in as " identity])
        [:div.buttons
         (if signed-in?
           [:a.button {:href "/sign-out"} "Sign out"]
           [:a.button.is-primary {:href "/sign-in"} "Sign in"])]]]]]))

(defn base [{{:keys [identity]} :session} content & [{{:keys [type text] :as message} :message :as params}]]
  (hp/html5
   (header params)
   (navbar (if identity
             (assoc params :signed-in? true :identity identity)
             params))
   [:section.section
    [:div.container
     [:article {:class (str "message "
                            (if message "" "is-hidden ")
                            (if message
                              (case type
                                :info "is-info"
                                :success "is-success"
                                :warn "is-warning"
                                :error "is-danger"
                                "is-primary")
                              ""))}
      [:div.message-header
       [:p (case type
             :info "Info"
             :success "Success"
             :warn "Warning"
             :error "danger"
             "Message")]
       [:button.delete {:aria-label :delete}]]
      [:div.message-body
       text]]
     content]]
   footer))

(defn go-back [model]
  [:a.is-link {:href (str "/" model "s")} "Back"])

(defn action-buttons [model id]
  [:div.buttons
   [:a {:href (str "/" model "s/" id "/edit")}
    [:span.icon.is-medium
     [:i.mdi.mdi-square-edit-outline.mdi-24px.mdi-dark]]]
   [:a {:href (str "/" model "s/" id "/delete")}
    [:span.icon.has-text-danger.is-medium
     [:i.mdi.mdi-delete.mdi-24px]]]])

(defn card-footer-edit [model id]
  [:a.card-footer-item {:href (str "/" model "s/" id "/edit")} "Edit"])

(defn card-footer-delete [model id]
  [:a.card-footer-item.has-text-danger {:href (str "/" model "s/" id "/delete")} "Delete"])

(defn card-footer-show [model id]
  [:a.card-footer-item {:href (str "/" model "s/" id "/show")} "Show"])

(defn details
  "Details card for model with header, table contents, and footer with actions"
  [model view tbody actions & {:keys [title-postfix thead listing? back-btn? actions?]
                               :or {title-postfix ""
                                    listing? false
                                    back-btn? true}}]
  [:div.content
   [:div.card
    [:header.card-header
     [:p.card-header-title (s/join " " [view (str (s/capitalize model) (if listing? "s" "")) title-postfix])]]
    [:div.card-content
     [:div.content
      [:table.table
       (when thead
         [:thead thead])
       [:tbody
        tbody]]
      (when (empty? tbody)
        [:p.has-text-grey.center (str "No results found.")])]
     [:footer.card-footer
      (when back-btn?
        [:div.card-footer-item
         [:a {:href "#" :onclick "(function () { window.history.back();})()"} "Back"]])
      (for [action actions]
        action)]]]])

(defn- create-query-str [params]
  (if-not (empty? params)
    (str "?" (reduce (partial clojure.string/join "&") (map (fn [[k v]] (str (name k) "=" v)) params)))
    ""))

(defn index [{:keys [attrs rows model actions buttons]
              :or {actions {:new {}}
                   buttons {:edit {:icon :square-edit-outline}
                            :delete {:icon :delete
                                     :type :danger}}}}]
  (let [thead [:tr
               (for [a attrs]
                 [:th a])
               [:td ""]]
        tbody (for [row rows]
                (let [id (str (first row))]
                  [:tr
                   [:th
                    [:a.is-link
                     {:href (str "/" model "s/" id "/show")}
                     id]]
                   (for [r (rest row)]
                     (cond
                       (vector? r) (if (vector? (first r))
                                     [:td
                                      (for [[ref text] r]
                                        [:a.index-refs {:href ref} text])]
                                     (let [[ref text] r]
                                       [:td [:a {:href ref} text]]))
                       (boolean? r) [:td
                                     (if r
                                       [:span.icon.has-text-success
                                        [:i.mdi.mdi-check-circle]]
                                       [:span.icon.has-text-danger
                                        [:i.mdi.mdi-close-circle]])]
                       :else [:td r]))
                   [:td
                    [:div.buttons
                     (for [[k {:keys [icon type]}] buttons]
                       [:a {:href (str model "s/" id "/" (name k))}
                        [:span {:class (str "icon is-medium " (case type
                                                                :danger "has-text-danger"
                                                                :info "has-text-info"
                                                                :success "has-text-success"
                                                                :warning "has-text-warning"
                                                                ""))}
                         [:i {:class (str "mdi mdi-24px mdi-" (name icon))}]]])]]]))
        action-items (for [[a params] actions]
                       [:a.card-footer-item {:href (str "/" model "s/" (name a) (create-query-str params))}
                        (str (s/capitalize (name a)) " " (s/capitalize model))])]
    (details model "Listing" tbody action-items :thead thead :listing? true :back-btn? false)))

(defn new [{:keys [attrs model submit-params title-postfix]}]
  (let [tbody (for [[k {:keys [type placeholder min max]}] attrs]
                (let [attr (name k)]
                  [:tr
                   [:th k]
                   [:td
                    (case type
                      :multi-selection [:div.select.is-mulitple
                                        {:style "margin-bottom: 10rem !important;"}
                                        [:select {:id attr
                                                  :size "5"
                                                  :multiple true
                                                  :name attr}
                                         (for [[oid oname] placeholder]
                                           [:option {:value (if (keyword oid)
                                                              (name oid)
                                                              oid)
                                                     :selected (= oid :employee)}
                                            (name oname)])]]
                      :selection [:div.select [:select {:id attr
                                                        :name attr}
                                               (for [[oid oname] placeholder]
                                                 [:option {:value (if (keyword oid)
                                                                    (name oid)
                                                                    oid)}
                                                  (name oname)])]]
                      [:input.input {:id attr
                                     :name attr
                                     :type type
                                     :min min
                                     :max max
                                     :placeholder placeholder}])]]))
        actions [[:input.card-footer-item.card-action-item {:type :submit :value "Save"}]]]
    [:form {:action (str "/" model "s" (create-query-str submit-params))
            :method "POST"}
     (anti-forgery-field)
     (details model "New" tbody actions :title-postfix title-postfix)]))

(defn show [{:keys [model entity id actions is-manager?]
             :or {actions {:edit {}
                           :delete {:type :danger}}
                  is-manager? true}}]
  (let [tbody (for [[k v] entity]
                [:tr
                 [:th (name k)]
                 [:td
                  (cond
                    (vector? v) (if (vector? (first v))
                                  (for [[ref text] v]
                                    [:a {:href ref} text])
                                  (let [[ref text] v]
                                    [:a {:href ref} text]))
                    (boolean? v) (if v
                                   [:span.icon.has-text-success
                                    [:i.mdi.mdi-check-circle]]
                                   [:span.icon.has-text-danger
                                    [:i.mdi.mdi-close-circle]])
                    :else v)]])
        action-items (if is-manager? (mapv
                                      (fn [[k {:keys [params type]}]]
                                        [:a {:href (str "/" model "s/" id "/" (name k) (create-query-str params))
                                             :class (case type
                                                      :danger "card-footer-item has-text-danger"
                                                      "card-footer-item")}
                                         (-> k name s/capitalize)])
                                      actions)
                         [])]
    (details model "Show" tbody action-items :title-postfix id)))

(defn question [{:keys [model entity id action value type]}]
  (let [capitalized (s/capitalize value)
        tbody (for [[k v] entity]
                [:tr
                 [:th (name k)]
                 [:td
                  (cond
                    (vector? v) (if (vector? (first v))
                                  (for [[ref text] v]
                                    [:a {:href ref} text])
                                  (let [[ref text] v]
                                    [:a {:href ref} text]))
                    (boolean? v) (if v
                                   [:span.icon.has-text-success
                                    [:i.mdi.mdi-check-circle]]
                                   [:span.icon.has-text-danger
                                    [:i.mdi.mdi-close-circle]])
                    :else v)]])
        actions [[:div.card-footer-item
                  [:form {:action (str "/" model "s/" id "/" (name action))
                          :method "POST"}
                   (anti-forgery-field)
                   [:input.input.is-hidden {:id "_method"
                                            :name "_method"
                                            :value value}]
                   [:input {:type :submit
                            :class (case type
                                     :danger "card-action-item has-text-danger"
                                     "card-action-item")
                            :value capitalized}]]]]]
    (details model capitalized tbody actions :title-postfix id)))

(defn delete [{:keys [model entity id]}]
  (let [tbody (for [[k v] entity]
                [:tr
                 [:th (name k)]
                 [:td
                  (cond
                    (vector? v) (if (vector? (first v))
                                  (for [[ref text] v]
                                    [:a {:href ref} text])
                                  (let [[ref text] v]
                                    [:a {:href ref} text]))
                    (boolean? v) (if v
                                   [:span.icon.has-text-success
                                    [:i.mdi.mdi-check-circle]]
                                   [:span.icon.has-text-danger
                                    [:i.mdi.mdi-close-circle]])
                    :else v)]])
        actions [[:div.card-footer-item
                  [:form {:action (str "/" model "s/" id "/patch")
                          :method "POST"}
                   (anti-forgery-field)
                   [:input.input.is-hidden {:id "_method"
                                            :name "_method"
                                            :value "delete"}]
                   [:input.card-action-item.has-text-danger {:type :submit
                                                             :value "Delete"}]]]]]
    (details model "Delete" tbody actions :title-postfix id)))



(defn edit [{:keys [model attrs values id]}]
  (let [tbody (for [[k {:keys [type placeholder]}] attrs]
                (let [attr (name k)]
                  [:tr
                   [:th k]
                   [:td
                    (case type
                      :multi-selection [:div.select.is-mulitple
                                        {:style "margin-bottom: 10rem !important;"}
                                        [:select {:id attr
                                                  :size "5"
                                                  :multiple true
                                                  :name attr}
                                         (for [[oid oname] placeholder]
                                           [:option {:value (if (keyword oid)
                                                              (name oid)
                                                              oid)
                                                     :selected (= oid (k values))}
                                            (name oname)])]]
                      :selection [:div.select [:select {:id attr
                                                        :name attr}
                                               (for [[oid oname] placeholder]
                                                 [:option {:value (if (keyword oid)
                                                                    (name oid)
                                                                    oid)
                                                           :selected (= oid (first (k values)))} (name oname)])]]
                      [:input.input {:id attr
                                     :name attr
                                     :type type
                                     :value (k values)
                                     :placeholder placeholder}])]]))
        actions [(card-footer-show model id)
                 [:input.card-footer-item.card-action-item {:type :submit :value "Save"}]]]
    [:form {:action (str "/" model "s/" id "/patch")
            :method "POST"}
     (anti-forgery-field)
     (details model "Edit" tbody actions)]))

(defn welcome [{:keys [session]}]
  [:section.hero
   [:div.hero-body
    [:div.container
     [:h1.title (if-let [id (:identity session)]
                  (str "Welcome " id)
                  (str "Not logged in."))]]]])

(defn sign-in []
  [:div.columns.is-centered
   [:div.column.card.is-half
    [:header.card-header
     [:p.card-header-title "Sign in"]]
    [:form {:action "/sign-in" :method "POST"}
     (anti-forgery-field)
     [:div.card-content
      [:div.content
       [:div.field
        [:label.label {:for "email"} "Email"]
        [:input.input {:id "email"
                       :name "email"
                       :type "email"
                       :placeholder "max@mustermann.de"}]]
       [:div.field
        [:label.label {:for "password"} "Password"]
        [:input.input {:id "password"
                       :type "password"
                       :name "password"
                       :placeholder "12345"}]]]
      [:footer.card-footer
       [:input.card-footer-item.card-action-item {:type :submit :value "Sign in"}]]]]]])

(defn container [& more]
  [:div.container
   more])

(defn render
  "renders the HTML template located relative to resources/html"
  [request content & [params]]
  (content-type
   (ok
    (base request content params))
   "text/html; charset=utf-8"))

(defn render-template
  "renders the HTML template located relative to resources/html"
  [_ template & [params]]
  (content-type
   (ok
    (parser/render-file
     template
     (assoc params
            :page template
            :csrf-token *anti-forgery-token*)))
   "text/html; charset=utf-8"))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})
