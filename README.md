# Usage 

## Read
```java
Gray[] gr = APNG.read(new File("my.apng"));
```

## Write
```java
byte x = (byte) 255;
byte[] a0 = {
        0, x, 0,
        x, 0, x,
        0, x, 0 };
byte[] a1 = {
        0, x, 0,
        0, x, 0,
        0, x, 0 };
byte[] a2 = {
        x, 0, x,
        x, 0, x,
        x, 0, x };

Gray[] g = new Gray[] {
        new Gray(3, 3, a0, APNG.DELAY_1S),
        new Gray(3, 3, a1, APNG.DELAY_1S),
        new Gray(3, 3, a2, APNG.DELAY_1S)};
        
File f = new File("my.apng");
f.createNewFile();
APNG.write(g, f, APNG.INFINITE_LOOP);
```

![resulting images](result.jpg)

![resulting animated image](result.png)

