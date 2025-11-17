package main;

import libraries.lua.com.lua.LuaAPIRegistry;
import modules.mainmodule;

import libraries.lua.com.lua.lua.*;

import static libraries.lua.com.lua.lua.*;

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
        try {
            table table = new table();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            LuaAPIRegistry.registerMinecraftAPIs();
        } catch (Exception e) {e.printStackTrace();}
        mainmodule.sayHello();
        System.out.println(String.valueOf(mainmodule.testboolean));
    }
}