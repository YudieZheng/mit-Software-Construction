/* Copyright (c) 2007-2016 MIT 6.005 course staff, all rights reserved.
 * Redistribution of original or derived work requires permission of course staff.
 */
package minesweeper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import minesweeper.server.MinesweeperServer;

/**
 * TODO: Specification
 */
public class Board {
    private static final int[][] directions = {{-1, 1}, {0, 1}, {1, 1}, {-1, 0}, {1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final double BOOMPROBABILITY = 0.25;
    private final int sizeX;
    private final int sizeY;
    private final ConcurrentHashMap<Point, Cell> cells = new ConcurrentHashMap<>();
    

    private static class Cell{
        boolean dug;
        boolean flagged;
        boolean boom;
        int neighbourBooms;
        private final Object lock = new Object();

        Cell(){
            synchronized (lock){
                this.dug = false;
                this.flagged = false;
                this.boom = ThreadLocalRandom.current().nextDouble() < BOOMPROBABILITY;
            }
        }

        Cell(boolean boom){
            synchronized (lock){
                this.dug = false;
                this.flagged = false;
                this.boom = boom;
            }
        }



        public boolean isDug(){
            synchronized (lock){
                return dug;
            }
        }

        public void setDug(boolean dug){
            synchronized (lock){
                this.dug = dug;
            }
        }

        public void setNeighbourBooms(int neighbourBooms) {
            synchronized (lock){
                this.neighbourBooms = neighbourBooms;
            }
        }

        public int getNeighbourBooms(){
            synchronized (lock){
                return neighbourBooms;
            }
        }

        public void decreaseneighbourBoomsBy1(){
            synchronized (lock){
                this.neighbourBooms -= 1;
            }
        }

        public boolean isflagged(){
            synchronized (lock){
                return flagged;
            }
        }

        public void setflagged(boolean flagged){
            synchronized (lock){
                this.flagged = flagged;
            }
        }

        public boolean isboom(){
            synchronized (lock){
                return boom;
            }
        }

        public void setboom(boolean boom){
            synchronized (lock){
                this.boom = boom;
            }
        }


    }

    private static class Point{
        final int x;
        final int y;

        Point(int x, int y){
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o){
            if (this == o){
                return true;
            }
            if (o == null || getClass() != o.getClass()){
                return false;
            }
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode(){
            return 31 * x + y;
        }
    }

    public Board(int sizeX, int sizeY){
        this.sizeX = sizeX;
        this.sizeY = sizeY;

        initializeCells();
    }

    public int getSizeX(){
        return sizeX;
    }

    public int getSizeY(){
        return sizeY;
    }

    public Board(File file, int defaultSize) {
        int tempSizeX = defaultSize;
        int tempSizeY = defaultSize;
        ConcurrentHashMap<Point, Cell> tempCells = new ConcurrentHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String firstLine = br.readLine();
            String[] size = firstLine.trim().split(" ");
            tempSizeX = Integer.parseInt(size[0]);
            tempSizeY = Integer.parseInt(size[1]);

            String line;
            int y = 0;
            while ((line = br.readLine()) != null) {
                String[] numbers = line.trim().split(" "); 
                if (numbers.length == tempSizeX) {
                    for (int i = 0; i < numbers.length; i++) {
                        int boom = Integer.parseInt(numbers[i]);
                        tempCells.put(new Point(i, y), new Cell(boom == 1));
                    }
                } else {
                    throw new Exception("File format error, we will use the default setting to generate the game!");
                }
                y++;
            }
            if (y != tempSizeY) {
                throw new Exception("File format error, we will use the default setting to generate the game!");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            tempSizeX = defaultSize;
            tempSizeY = defaultSize;
            tempCells = new ConcurrentHashMap<>();
            for (int i = 0; i < defaultSize; i++) {
                for (int j = 0; j < defaultSize; j++) {
                    tempCells.put(new Point(i, j), new Cell());
                }
            }
        }

        this.sizeX = tempSizeX;
        this.sizeY = tempSizeY;
        this.cells.putAll(tempCells);
    }

    private void initializeCells(){
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                Point point = new Point(i, j);
                cells.put(point, new Cell());
            }
        }

        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                int nbrbooms = 0;
                for (int[] js : directions) {
                    int newX = i + js[0];
                    int newY = j + js[1];
                    if ( !isOutOfBound(newX, sizeX) && !isOutOfBound(newY, sizeY)){
                        boolean boom = cells.get(new Point(newX, newY)).isboom();
                        if (boom){
                            nbrbooms += 1;
                        }
                    }
                }
                cells.get(new Point(i, j)).setNeighbourBooms(nbrbooms);
            }
        }
    }

    private boolean isOutOfBound(int x, int size){
        return x < 0 || x >= size;
    }

    public String dig(int x, int y){
        if (isOutOfBound(x, sizeX) || isOutOfBound(y, sizeY)){
            return BoardMessage(); 
        }
        Cell cell = cells.get(new Point(x, y));
        boolean dug = cell.isDug();
        if (dug){
            return BoardMessage(); 
        }
        boolean boom = cell.isboom();
        if (boom){
            cell.setboom(false);
            decreaseneighboursBoomsBy1(x, y);
            return MinesweeperServer.BOOMMESSAGE;
        }

        cell.setDug(true);
        if (cell.getNeighbourBooms() != 0){
            // do nothing
        }else{
            for (int[] js : directions) {
                int newX = x + js[0];
                int newY = y + js[1];
                recursiveDig(newX, newY);
            }
        }
        return BoardMessage();
    }

    private void recursiveDig(int x, int y){
        if (isOutOfBound(x, sizeX) || isOutOfBound(y, sizeY)){
            return;
        }
        Cell cell = cells.get(new Point(x, y));
        if(cell.isDug()){
            return;
        }else{
            cell.setDug(true);
            if (cell.getNeighbourBooms() != 0){
                return;
            }else{
                for (int[] js : directions){
                    int newX = x + js[0];
                    int newY = y + js[1];
                    recursiveDig(newX, newY);
                }
            }
        }
    }

    private String BoardMessage(){
        StringBuffer boardState = new StringBuffer();
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                if (!cells.get(new Point(i, j)).dug){
                    boardState.append("- ");
                }else if (cells.get(new Point(i, j)).flagged){
                    boardState.append("F ");
                }else{
                    if (cells.get(new Point(i, j)).neighbourBooms == 0){
                        boardState.append("  ");
                    }else{
                        boardState.append(cells.get(new Point(i, j)).neighbourBooms + " ");
                    }
                }
            }
            boardState.deleteCharAt(boardState.length() - 1);
            boardState.append("\r\n");
        }
        return boardState.toString();
    }

    public String getBoardMessage(){
        return BoardMessage();
    }

    private void decreaseneighboursBoomsBy1(int x, int y){
        for (int[] js : directions) {
            int newX = x + js[0];
            int newY = y + js[1];
            if ( !isOutOfBound(newX, sizeX) && !isOutOfBound(newY, sizeY)){
                Cell cell = cells.get(new Point(newX, newY));
                cell.decreaseneighbourBoomsBy1();
            }
        }
    }

    public String flag(int x, int y){
        if ( !isOutOfBound(x, sizeX) && !isOutOfBound(y, sizeY)){
            Cell cell = cells.get(new Point(x, y));
            if (!cell.isDug() && !cell.isflagged()){
                cell.setflagged(true);
            }
        }
        return BoardMessage();
    }

    public String deflag(int x, int y){
        if ( !isOutOfBound(x, sizeX) && !isOutOfBound(y, sizeY)){
            Cell cell = cells.get(new Point(x, y));
            if (!cell.isDug() && cell.isflagged()){
                cell.setflagged(false);
            }
        }
        return BoardMessage();
    }
}
