package com.getbouncer.cardscan.base.ml.models.legacy;

import com.getbouncer.cardscan.base.domain.DetectionBox;
import com.getbouncer.cardscan.base.ml.models.FindFourModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 Organize the boxes to find possible numbers.

 After running detection, the post processing algorithm will try to find
 sequences of boxes that are plausible card numbers. The basic techniques
 that it uses are non-maximum suppression and depth first search on box
 sequences to find likely numbers. There are also a number of heuristics
 for filtering out unlikely sequences.
 */

class PostDetectionAlgorithm {
    private final int kNumberWordCount = 4;
    private final int kAmexWordCount = 5;
    private final int kMaxBoxesToDetect = 20;
    private final int kDeltaRowForCombine = 2;
    private final int kDeltaColForCombine = 2;
    private final int kDeltaRowForHorizontalNumbers = 1;
    private final int kDeltaColForVerticalNumbers = 1;

    private ArrayList<DetectionBox> sortedBoxes;
    private final int numRows;
    private final int numCols;

    private static Comparator<DetectionBox> colCompare = (o1, o2) -> Integer.compare(o1.getCol(), o2.getCol());

    private static Comparator<DetectionBox> rowCompare = (o1, o2) -> Integer.compare(o1.getRow(), o2.getRow());

    PostDetectionAlgorithm(ArrayList<DetectionBox> boxes) {
        this.numCols = FindFourModel.COLS;
        this.numRows = FindFourModel.ROWS;

        this.sortedBoxes = new ArrayList<>();
        Collections.sort(boxes);
        Collections.reverse(boxes);
        for (DetectionBox box:boxes) {
            if (this.sortedBoxes.size() >= kMaxBoxesToDetect) {
                break;
            }
            this.sortedBoxes.add(box);
        }
    }

    ArrayList<ArrayList<DetectionBox>> horizontalNumbers() {
        ArrayList<DetectionBox> boxes = this.combineCloseBoxes(kDeltaRowForCombine,
                kDeltaColForCombine);
        ArrayList<ArrayList<DetectionBox>> lines = this.findHorizontalNumbers(boxes, kNumberWordCount);

        ArrayList<ArrayList<DetectionBox>> linesOut = new ArrayList<>();
        // boxes should be roughly evenly spaced, reject any that aren't
        for (ArrayList<DetectionBox> line:lines) {
            ArrayList<Integer> deltas = new ArrayList<>();
            for (int idx = 0; idx < (line.size()-1); idx++) {
                deltas.add(line.get(idx+1).getCol() - line.get(idx).getCol());
            }

            Collections.sort(deltas);
            int maxDelta = deltas.get(deltas.size() - 1);
            int minDelta = deltas.get(0);

            if ((maxDelta - minDelta) <= 2) {
                linesOut.add(line);
            }
        }

        return linesOut;
    }

    ArrayList<ArrayList<DetectionBox>> amexNumbers() {
        ArrayList<DetectionBox> boxes = this.combineCloseBoxes(kDeltaRowForCombine, 1);
        ArrayList<ArrayList<DetectionBox>> lines = this.findHorizontalNumbers(boxes, kAmexWordCount);

        ArrayList<ArrayList<DetectionBox>> linesOut = new ArrayList<>();

        // we have roughly evenly spaced clusters. A single box of four, a cluster of 6 and then
        // a cluster of 5. We try to recognize the first and last few digits of the 5 and 6
        // cluster, and the 5 and 6 cluster are roughly evenly spaced but the boxes within
        // are close
        //
        // This logic is a bit messy in an effort to stay consistent with the iOS logic, which
        // makes heavy use of function idioms
        for (ArrayList<DetectionBox> line:lines) {
            ArrayList<Integer> colDeltas = new ArrayList<>();
            for (int idx = 1; idx < line.size(); idx++) {
                colDeltas.add(line.get(idx).getCol() - line.get(idx-1).getCol());
            }
            ArrayList<Integer> evenColDeltas = new ArrayList<>();
            ArrayList<Integer> oddColDeltas = new ArrayList<>();

            for (int idx = 0; idx < colDeltas.size(); idx++) {
                if ((idx % 2) == 0) {
                    evenColDeltas.add(colDeltas.get(idx));
                } else {
                    oddColDeltas.add(colDeltas.get(idx));
                }
            }

            boolean areGapsBigEnough = true;
            for (int idx = 0; idx < evenColDeltas.size(); idx++) {
                float even = (float) evenColDeltas.get(idx);
                float odd = (float) oddColDeltas.get(idx);

                areGapsBigEnough = areGapsBigEnough && ((even / odd) >= 2.0);
            }

            if (areGapsBigEnough) {
                linesOut.add(line);
            }
        }

        return linesOut;
    }

    ArrayList<ArrayList<DetectionBox>> verticalNumbers() {
        ArrayList<DetectionBox> boxes = this.combineCloseBoxes(kDeltaRowForCombine,
                kDeltaColForCombine);
        ArrayList<ArrayList<DetectionBox>> lines = this.findVerticalNumbers(boxes);

        ArrayList<ArrayList<DetectionBox>> linesOut = new ArrayList<>();
        // boxes should be roughly evenly spaced, reject any that aren't
        for (ArrayList<DetectionBox> line:lines) {
            ArrayList<Integer> deltas = new ArrayList<>();
            for (int idx = 0; idx < (line.size()-1); idx++) {
                deltas.add(line.get(idx+1).getRow() - line.get(idx).getRow());
            }

            Collections.sort(deltas);
            int maxDelta = deltas.get(deltas.size() - 1);
            int minDelta = deltas.get(0);

            if ((maxDelta - minDelta) <= 2) {
                linesOut.add(line);
            }
        }

        return linesOut;
    }

    private boolean horizontalPredicate(DetectionBox currentWord, DetectionBox nextWord) {
        int deltaRow = kDeltaRowForHorizontalNumbers;
        return nextWord.getCol() > currentWord.getCol() && nextWord.getRow() >= (currentWord.getRow()-deltaRow) &&
                nextWord.getRow() <= (currentWord.getRow()+deltaRow);
    }

    private boolean verticalPredicate(DetectionBox currentWord, DetectionBox nextWord) {
        int deltaCol = kDeltaColForVerticalNumbers;
        return nextWord.getRow() > currentWord.getRow() && nextWord.getCol() >= (currentWord.getCol()-deltaCol) &&
                nextWord.getCol() <= (currentWord.getCol()+deltaCol);
    }

    private void findNumbers(ArrayList<DetectionBox> currentLine, ArrayList<DetectionBox> words,
                             boolean useHorizontalPredicate, int numberOfBoxes,
                             ArrayList<ArrayList<DetectionBox>> lines) {
        if (currentLine.size() == numberOfBoxes) {
            lines.add(currentLine);
            return;
        }

        if (words.size() == 0) {
            return;
        }

        DetectionBox currentWord = currentLine.get(currentLine.size() - 1);
        if (currentWord == null) {
            return;
        }


        for (int idx = 0; idx < words.size(); idx++) {
            DetectionBox word = words.get(idx);
            if (useHorizontalPredicate && horizontalPredicate(currentWord, word)) {
                ArrayList<DetectionBox> newCurrentLine = new ArrayList<>(currentLine);
                newCurrentLine.add(word);
                findNumbers(newCurrentLine, dropFirst(words, idx+1), useHorizontalPredicate,
                        numberOfBoxes, lines);
            } else if (verticalPredicate(currentWord, word)) {
                ArrayList<DetectionBox> newCurrentLine = new ArrayList<>(currentLine);
                newCurrentLine.add(word);
                findNumbers(newCurrentLine, dropFirst(words, idx+1), useHorizontalPredicate,
                        numberOfBoxes, lines);
            }
        }
    }

    private ArrayList<DetectionBox> dropFirst(ArrayList<DetectionBox> boxes, int n) {
        ArrayList<DetectionBox> result = new ArrayList<>();
        for (int idx = n; idx < boxes.size(); idx++) {
            result.add(boxes.get(idx));
        }
        return result;
    }

    // Note: this is simple but inefficient. Since we're dealing with small
    // lists (eg 20 items) it should be fine
    private ArrayList<ArrayList<DetectionBox>> findHorizontalNumbers(ArrayList<DetectionBox> words,
                                                                    int numberOfBoxes) {
        Collections.sort(words, colCompare);
        ArrayList<ArrayList<DetectionBox>> lines = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            ArrayList<DetectionBox> currentLine = new ArrayList<>();
            currentLine.add(words.get(idx));
            findNumbers(currentLine, dropFirst(words, idx+1), true,
                    numberOfBoxes, lines);
        }

        return lines;
    }

    private ArrayList<ArrayList<DetectionBox>> findVerticalNumbers(ArrayList<DetectionBox> words) {
        int numberOfBoxes = 4;
        Collections.sort(words, rowCompare);
        ArrayList<ArrayList<DetectionBox>> lines = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            ArrayList<DetectionBox> currentLine = new ArrayList<>();
            currentLine.add(words.get(idx));
            findNumbers(currentLine, dropFirst(words, idx+1), false,
                    numberOfBoxes, lines);
        }

        return lines;
    }

    /**
     Combine close boxes favoring high confidence boxes.
     */
    private ArrayList<DetectionBox> combineCloseBoxes(int deltaRow, int deltaCol) {
        boolean[][] cardGrid = new boolean[this.numRows][this.numCols];
        for (int row = 0; row < this.numRows; row++) {
            for (int col = 0; col < this. numCols; col++) {
                cardGrid[row][col] = false;
            }
        }

        for (DetectionBox box:this.sortedBoxes) {
            cardGrid[box.getRow()][box.getCol()] = true;
        }

        // since the boxes are sorted by confidence, go through them in order to
        // result in only high confidence boxes winning. There are corner cases
        // where this will leave extra boxes, but that's ok because we don't
        // need to be perfect here
        for (DetectionBox box:this.sortedBoxes) {
            if (!cardGrid[box.getRow()][box.getCol()]) {
                continue;
            }
            for (int row = (box.getRow() - deltaRow); row <= (box.getRow() + deltaRow); row++) {
                for (int col = (box.getCol() - deltaCol); col <= (box.getCol() + deltaCol); col++) {
                    if (row >= 0 && row < this.numRows && col >= 0 && col < this.numCols) {
                        cardGrid[row][col] = false;
                    }
                }
            }

            // add this box back
            cardGrid[box.getRow()][box.getCol()] = true;
        }

        ArrayList<DetectionBox> combinedBoxes = new ArrayList<>();
        for (DetectionBox box:this.sortedBoxes) {
            if (cardGrid[box.getRow()][box.getCol()]) {
                combinedBoxes.add(box);
            }
        }

        return combinedBoxes;

    }
}
