package com.example.missingpersons;

public class Person {

    private String name;
    private int age;
    private String lastLocation;
    private String lastClothes;
    private String contactPhone;

    public Person(String name, int age, String lastLocation, String lastClothes, String contactPhone) {
        this.name = name;
        this.age = age;
        this.lastLocation = lastLocation;
        this.lastClothes = lastClothes;
        this.contactPhone = contactPhone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(String lastLocation) {
        this.lastLocation = lastLocation;
    }

    public String getLastClothes() {
        return lastClothes;
    }

    public void setLastClothes(String lastClothes) {
        this.lastClothes = lastClothes;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
