Question 1:
Android Permissions utilized: 
 1. Coarse Location
 2. Internet Access
The app utilizes a REST API provided by weather.visualcrossing.com to retrieve the Max, Min temperature for a given day. The lat long is automatically queried from android's location services. To ensure that a non null location is provided, the
location services is actively polled to generate the location. The data is returned as a JSON packet. The JSON object is parsed, and subsequently the max min temperature is displayed to the user/
There are various validation checks to ensure the date is in a valid format AND is before the current date on the phone. If the internet is not working/enabled, the request gracefully times-out.


Question 2:
The same above foundation, except the following added functionaility:
  1. Local Database using Android's Room Library with it's various requisite methods and constructors. The database schema incorporates the date, max, min, lat, lon
  2. After succesfully requesting the Max, Min Temperature for a given day, there is a button to commit this data into the database. If the data is null, the app does not crash.
  3. The local database can be queried by inputing the date, and pressing a button. If no record is found an error message is displayed. If the date is in the future, it calculates the average of the previous 10 datapoints on the same date (diff year).
     If there are not 10 data points, an error message is displayed.

Where appropriate, coroutines are used to ensure that he main ui thread isnt bogged down.
