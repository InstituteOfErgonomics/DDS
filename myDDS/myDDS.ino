//------------------------------------------------------
//Revision History 'myDDS'
//------------------------------------------------------
//Version  Date		Author		  Mod
//1        Aug, 2014	Michael Krause	  initial
//
//------------------------------------------------------
/*
The MIT License (MIT)

        Copyright (c) 2015 Michael Krause (krause@tum.de), Institute of Ergonomics, Technische Universität München

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in
        all copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
        THE SOFTWARE.

*/

#define BLUETOOTH_SPEED 57600

#include <SoftwareSerial.h>
SoftwareSerial mySerial(9, 8); // RX, TX

//pins for four way switch (simple short to GND switches => pull up: low active)
const int LEFT_PIN = 2; 
const int DOWN_PIN = 3; 
const int UP_PIN = 4; 
const int RIGHT_PIN = 5; 

//pins for rotary phases (light barrier, optical sensor)
const int ROT_A_PIN = A3; 
const int ROT_B_PIN = A4; 

//pins for push (light barrier, optical sensor)
const int PUSH_PIN = A5; 

#define SEPCIAL_CHAR    "#"
//events
#define TURN    "t"
#define PRESS   "P"
#define HOLD    "H"
#define RELEASE "R"

//what caused the event
#define TURN_LEFT  'l'
#define TURN_RIGHT 'r'
#define PUSH       'P'
#define UP         'U'
#define DOWN       'D'
#define LEFT       'L'
#define RIGHT      'R'

#define DEBOUNCE_US 50000 
//50ms
#define REPEAT_US 150000
//200ms => 5Hz  repeat rate if hold
#define INITIAL_US 500000
//300ms before begin repeat

typedef struct myButton{
  byte state;
  unsigned long t_lastPress;
  unsigned long t_lastRelease;
  unsigned long t_lastRepeat;
  unsigned long repeat;
  unsigned long initialDelay;//before repeat
  byte senderCode;
  byte active;//if activeLow == 0 if activeHigh==1
} sButton, *pButton; 

void handleButton(unsigned long t_now, byte state, pButton button);
//--------
sButton bUp;
sButton bDown;
sButton bLeft;
sButton bRight;
sButton bPush;
byte last_rot_a;
byte last_rot_b;

//-----------------------------------------
void setup()
{
  Serial.begin(115200);
  while (!Serial) {
    ; // wait for serial
  }
  Serial.println("Starting");
  mySerial.begin(BLUETOOTH_SPEED);
  delay(1000);
  
  // Should respond with OK
  mySerial.println("AT");
  delay(1000);
  echoResponseOnSerial();
  mySerial.println("Hello from DDS via Bluetooth!");

  pinMode(UP_PIN, INPUT); 
  digitalWrite(UP_PIN, HIGH); // pullUp on  
  pinMode(DOWN_PIN, INPUT); 
  digitalWrite(DOWN_PIN, HIGH); // pullUp on  
  pinMode(LEFT_PIN, INPUT); 
  digitalWrite(LEFT_PIN, HIGH); // pullUp on  
  pinMode(RIGHT_PIN, INPUT); 
  digitalWrite(RIGHT_PIN, HIGH); // pullUp on  


  pinMode(ROT_A_PIN, INPUT);
  last_rot_a =   digitalRead(ROT_A_PIN);
  pinMode(ROT_B_PIN, INPUT); 
  last_rot_b =   digitalRead(ROT_B_PIN);
  pinMode(PUSH_PIN, INPUT); 
  bPush.state = digitalRead(PUSH_PIN);
  bPush.active = 1;
  bPush.repeat = REPEAT_US;
  bPush.initialDelay = INITIAL_US;
  bPush.senderCode = PUSH;
  
  //init
  bUp.state = digitalRead(UP_PIN);
  bUp.active = 0;
  bUp.repeat = REPEAT_US;
  bUp.initialDelay = INITIAL_US;
  bUp.senderCode = UP;
    
  bDown.state = digitalRead(DOWN_PIN);
  bDown.active = 0;
  bDown.repeat = REPEAT_US;
  bDown.initialDelay = INITIAL_US;  
  bDown.senderCode = DOWN;
    
  bLeft.state = digitalRead(LEFT_PIN);
  bLeft.active = 0;
  bLeft.repeat = REPEAT_US;
  bLeft.initialDelay = INITIAL_US;    
  bLeft.senderCode = LEFT;
    
  bRight.state = digitalRead(RIGHT_PIN);
  bRight.active = 0;
  bRight.repeat = REPEAT_US;
  bRight.initialDelay = INITIAL_US;   
  bRight.senderCode = RIGHT;
  
  Serial.println("Setup done");
}

void echoResponseOnSerial(){
    while (mySerial.available()) {
      Serial.write(mySerial.read());
    }
    Serial.write("\n");
}

//------------------------------------------------
void sendSerial(const char* event, byte causedBy){
  
    mySerial.print(SEPCIAL_CHAR);
    mySerial.print(event);
    mySerial.println(char(causedBy));
    
    Serial.print(SEPCIAL_CHAR);
    Serial.print(event);
    Serial.println(char(causedBy));    
}
//------------------------------------------------
void handleButton(unsigned long t_now, byte state, pButton button){
  
  if(state != button->state){
    if(state == button->active){
      if(t_now - (button->t_lastRelease) > DEBOUNCE_US){//press
        button->state = state;
        sendSerial(PRESS, button->senderCode);
        button->t_lastPress = t_now;
        //button->t_lastRepeat = t_now;
      }//end press
    }else{//state != button.active
      if((t_now - button->t_lastPress) > DEBOUNCE_US){//release
        button->state = state;
        sendSerial(RELEASE, button->senderCode);
        button->t_lastRelease = t_now;       
      }//end release
    }
  }else{//state == button.state
    if((state == button->active) && ((t_now - button->t_lastPress) >button->initialDelay) && ((t_now - button->t_lastRepeat) > button->repeat)){//repeat
        sendSerial(HOLD, button->senderCode);
        button->t_lastRepeat = t_now;
    }
  }
 
}
//------------------------------------------------
void loop(){
  //read in
  int up = digitalRead(UP_PIN);
  int down = digitalRead(DOWN_PIN);
  int left = digitalRead(LEFT_PIN);
  int right = digitalRead(RIGHT_PIN);
  int rot_a = digitalRead(ROT_A_PIN);
  int rot_b = digitalRead(ROT_B_PIN);
  int push = digitalRead(PUSH_PIN);
  
  unsigned long t_now = micros();
  
  handleButton(t_now, up, &bUp);
  handleButton(t_now, down, &bDown);
  handleButton(t_now, left, &bLeft);
  handleButton(t_now, right, &bRight);
  handleButton(t_now, push, &bPush);
  
  if((last_rot_a != rot_a) || (last_rot_b != rot_b)){
    if (rot_a != rot_b){
      if (last_rot_a != rot_a){
        sendSerial(TURN, TURN_LEFT);
      }else{
        sendSerial(TURN, TURN_RIGHT);        
      }
    }
  } 
  last_rot_a =   rot_a;
  last_rot_b =   rot_b; 
}
