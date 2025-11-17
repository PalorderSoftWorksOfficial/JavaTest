package main;

import modules.mainmodule;
public class Main {
    public static void main(String[] args) {
        mainmodule mainmodule = new mainmodule();
        try {
            System.out.println("loading config...");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mainmodule.configLoad();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mainmodule.sayHello();
        System.out.println(String.valueOf(mainmodule.testboolean));
    }
}
