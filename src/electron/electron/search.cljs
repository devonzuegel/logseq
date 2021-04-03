(ns electron.search
  (:require ["path" :as path]
            ["better-sqlite3" :as sqlite3]
            [clojure.string :as string]
            [electron.utils :refer [logger] :as utils]))

(def error (partial (.-error logger) "[Search]"))

(defonce database (atom nil))

(defn close!
  []
  (when @database
    (.close @database)
    (reset! database nil)))

(defn prepare
  [^object db sql]
  (when db
    (.prepare db sql)))

;; (defn add-triggers!
;;   [db]
;;   (let [triggers ["CREATE TRIGGER blocks_ai AFTER INSERT ON blocks
;;     BEGIN
;;         INSERT INTO blocks_fts (id, text)
;;         VALUES (new.id, new.text);
;;     END;
;; "
;;                   "CREATE TRIGGER blocks_ad AFTER DELETE ON blocks
;;     BEGIN
;;         INSERT INTO blocks_fts (blocks_fts, id, text)
;;         VALUES ('delete', old.id, old.text);
;;     END;"
;;                   "CREATE TRIGGER blocks_au AFTER UPDATE ON blocks
;;     BEGIN
;;         INSERT INTO blocks_fts (blocks_fts, id, text)
;;         VALUES ('delete', old.id, old.text);
;;         INSERT INTO blocks_fts (id, text)
;;         VALUES (new.id, new.text);
;;     END;"]]
;;     (doseq [trigger triggers]
;;      (let [stmt (prepare db trigger)]
;;        (.run ^object stmt)))))

(defn create-blocks-table!
  [db]
  (let [stmt (prepare db "CREATE TABLE IF NOT EXISTS blocks (
                        id INTEGER PRIMARY KEY,
                        uuid TEXT NOT NULL,
                        content TEXT NOT NULL)")]
    (.run ^object stmt)))

(defn create-blocks-fts-table!
  [db]
  (let [stmt (prepare db "CREATE VIRTUAL TABLE blocks_fts USING fts5(id, uuid, content)")]
    (.run ^object stmt)))

(defn open-db!
  []
  ;; TODO: where to store the search database
  (let [db-path (.join path "/tmp/logseq_search.db")
        db (sqlite3 db-path #js {:verbose js/console.log})
        _ (try (create-blocks-table! db)
               (catch js/Error e
                 (error e)))
        ;; _ (try (create-blocks-fts-table! db)
        ;;        (catch js/Error e
        ;;          (error e)))
        ;; _ (try (add-triggers! db)
        ;;        (catch js/Error e
        ;;          (error e)))
        ]
    (reset! database db)
    db))

(defonce debug-blocks (atom nil))
(defn upsert-blocks!
  [blocks]
  (reset! debug-blocks blocks)
  (when-let [db @database]
    ;; TODO: what if a CONFLICT on uuid
    (let [insert (prepare db "INSERT INTO blocks (id, uuid, content) VALUES (@id, @uuid, @content) ON CONFLICT (id) DO UPDATE SET content = @content")
          insert-many (.transaction ^object db
                                    (fn [blocks]
                                      (doseq [block blocks]
                                        (.run ^object insert block))))]
      (insert-many blocks))))

(defn delete-blocks!
  [ids]
  (when-let [db @database]
    (let [sql (utils/format "DELETE from blocks WHERE id IN (%s)"
                            (->> (map (fn [id] (str "'" id "'")) ids)
                                 (string/join ", ")))
          stmt (prepare db sql)]
      (.run ^object stmt))))

(defn get-all-blocks
  []
  (when-let [stmt (prepare @database
                       "select * from blocks")]
    (js->clj (.all ^object stmt) :keywordize-keys true)))

;; (defn search-blocks-fts
;;   [q]
;;   (when-not (string/blank? q)
;;     (let [stmt (prepare @database
;;                          "select id, uuid, content from blocks_fts where content match ? ORDER BY rank")]
;;       (js->clj (.all ^object stmt q) :keywordize-keys true))))

(defn search-blocks
  [q limit]
  (when-not (string/blank? q)
    (when-let [stmt (prepare @database
                        "select id, uuid, content from blocks where content like ? limit ?")]
      (js->clj (.all ^object stmt (str "%" q "%") limit) :keywordize-keys true))))

(defn truncate-blocks-table!
  []
  (let [stmt (prepare @database
                      "delete from blocks;")]
    (.run ^object stmt)))

(defn drop-blocks-table!
  []
  (let [stmt (prepare @database
                       "drop table blocks;")
        _ (.run ^object stmt)
        ;; stmt (prepare @database
        ;;               "drop table blocks_fts;")
        ]
    ;; (.run ^object stmt)
    ))


(comment
  (open-db!)

  (add-blocks! (clj->js [{:id "a"
                          :uuid ""
                          :content "hello world"}
                         {:id "b"
                          :uuid ""
                          :content "foo bar"}]))

  (time
    (let [blocks (for [i (range 10000)]
                   {:id (str i)
                    :uuid ""
                    :content (rand-nth ["hello" "world" "nice"])})]
      (add-blocks! (clj->js blocks))))

  (get-all-blocks)

  (search-blocks "hello")

  (def block {:id 16, :uuid "5f713e91-8a3c-4b04-a33a-c39482428e2d", :content "Hello, I'm a block!"})

  (add-blocks! (clj->js [block]))
  )