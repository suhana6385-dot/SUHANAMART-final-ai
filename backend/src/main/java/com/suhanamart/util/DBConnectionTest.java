package com.suhanamart.util;

import java.sql.Connection;

public class DBConnectionTest {

    public static void main(String[] args) {

        try (Connection connection = DBConnection.getConnection()) {

            System.out.println("=================================");
            System.out.println("DATABASE CONNECTION SUCCESSFUL!");
            System.out.println("Database: suhanamart");
            System.out.println("=================================");

        } catch (Exception e) {

            System.out.println("DATABASE CONNECTION FAILED!");
            e.printStackTrace();
        }
    }
}
