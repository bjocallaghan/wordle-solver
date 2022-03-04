(ns net.bjoc.wordle-solver.wordle
  (:require [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

(def wordle-word?
  "Returns truthy for a valid Wordle word.
  
  A five-letter word is a valid Wordle word."
  #(re-find #"^[a-z]{5}$" %))

(defn word->candidate
  "Transforming a String to a map.

  Doing it upfront for (mostly) convenience and (possibly) performance.
  Sometimes I want the String itself. sometimes I want to use the word as a
  Vector of letters so that I can easily get a letter at a certain
  position. Sometimes I want to use the letters as a Set to easily tell if some
  other letter is contained in the word."
  [word]
  {:word word
   :letters-vec (-> word seq vec)
   :letters-set (-> word seq set)})

(defn aspell-words
  "Return list of all words in the aspell dictionary.

  Probably not portable across platforms."
  []
  (->> (sh "aspell" "dump" "master")
       :out
       (#(str/split % #"\n"))))

(def candidates
  "The list of all (starting) Wordle candidate words."
  (->> (aspell-words)
       (filter wordle-word?)
       (map word->candidate)))

;;;

(def initial-state
  "State of possibilities/limitations.

  Initially this is empty, but the 'shape' supports accretion of info as more
  guesses are made.

  {:greens {}     ;; a map. key is letter, value is correct position
   :yellows {}    ;; a map. key is letter, value is set of incorrect locations
   :grays #{}})   ;; a set. all incorrect letters
  "
  {:greens {}
   :yellows {}
   :grays #{}})

(defn update-state
  "Update the state based on a guessed word and the result.

  Sample usage:
  (->> initial-state
       (update-state \"later\" \"...gy\")
       (update-state \"screw\" \"..yg.\")
       (update-state \"urged\" \"yy.g.\")
       )
  "
  [guessed result state]
  (let [letters (vec guessed)]
    (loop [i 0 state state]
      (if (= i 5)
        state
        (recur (inc i)
               (case ((vec result) i)
                 \g (assoc-in state [:greens (letters i)] i)
                 \y (if (get-in state [:yellows (letters i)])
                      (update-in state [:yellows (letters i)] conj i)
                      (assoc-in state [:yellows (letters i)] #{i}))
                 \. (update-in state [:grays] conj (letters i))))))))

;;;

(defn fits-greens-pred
  "Returns predicate that evaluates if a candidate matches known green letters."
  [greens]
  (apply every-pred
         (map (fn [[letter position]]
                (fn [{:keys [letters-vec] :as candidate}]
                  (= letter (get letters-vec position))))
              greens)))

(defn has-yellows-pred
  "Returns predicate that evaluates if a candidate has all yellow letters."
  [yellows]
  (apply every-pred
         (for [yellow-letter (keys yellows)]
           (fn [{:keys [letters-set]}]
             (letters-set yellow-letter)))))

(defn no-bad-yellows-pred
  "Returns predicate that evaluates if a candidate avoids yellow letters in
  known incorrect positions."
  [yellows]
  (apply every-pred
         (for [i (range 5)]
           (let [prohibited (->> yellows
                                 (filter (fn [[_ positions]] (positions i)))
                                 keys
                                 (into #{}))]
             (fn [{:keys [letters-vec]}]
               (not (prohibited (letters-vec i))))))))

(defn fits-grays-pred
  "Returns predicate that evaluates if a candidate avoids any known gray
  (unused) letters.

  A bit of weird preprocessing needs to be done to 'mask' definitely-used
  letters. I.e. A known letter can reappear, but if its second appearance is
  marked gray, that definitively means it does not reoccur."
  [{:keys [greens yellows grays]}]
  (fn [{:keys [letters-set] :as candidate}]
    (let [known-letters (concat (keys greens) (keys yellows))
          diminished-letters (reduce #(disj %1 %2) letters-set known-letters)]
      (not (some grays diminished-letters)))))

(defn fits-pred
  "Returns predicate that evaluates if a candidate fulfills all Wordle rules
  based on known state of previous guesses."
  [{:keys [greens yellows grays] :as state}]
  (let [preds* [(when-not (empty? greens)
                  (fits-greens-pred greens))
                (when-not (empty? yellows)
                  (no-bad-yellows-pred yellows))
                (when-not (empty? yellows)
                  (has-yellows-pred yellows))
                (fits-grays-pred state)]
        preds (remove nil? preds*)]
    (apply every-pred preds)))

;;;

(defn some-guesses
  "Suggest some guesses based on the current state."
  ([state] (some-guesses state candidates))
  ([state candidates]
   (->> candidates
        (filter (fits-pred state))
        (sort-by #(-> % :letters-set count -))
        (map :word)
        (take 10))))

(defn main
  "Main. For running from a terminal."
  [_]
  (try
    (loop [state initial-state]
      (let [suggested-guess (first (some-guesses state))]
        (when (nil? suggested-guess)
          (throw (ex-info "No valid guesses left!" state)))
        (println (format "Guess '%s'" suggested-guess))
        (print "Result: ")
        (let [result (read-line)]
          (if (= result "ggggg")
            (println "You win!")
            (recur (update-state suggested-guess result state))))))
    (catch Exception e
      (println (format "ERROR: %s" (.getMessage e))))))
