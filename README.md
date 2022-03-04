# What is this?

It's a [https://www.nytimes.com/games/wordle/index.html](Wordle) solver. You can
play the []("official") version or maybe an
[https://www.devangthakkar.com/wordle_archive/](unofficial Wordle archive).

Are there better, more sophisticated solvers? You bet! But this one is mine.

# Requirements

Must have [http://aspell.net/](Aspell) installed and on the path.

How to check? This command should work:

```
aspell dump master | head
```

# Instructions

To run:

```
clj -X net.bjoc.wordle-solver.wordle/main
```

The program will tell you what to guess. You then give it the result.

* `.` is a gray box
* `y` is a yellow box
* `g` is a green box

## Example
You guess the suggested word, and get Green, Gray, Yellow, Yellow,
Gray. You enter `g.yy.` when prompted for the result. You will then get another
suggestion. Repeat.
