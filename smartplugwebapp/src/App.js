import { CircularProgressbar } from 'react-circular-progressbar';
import Switch from "react-switch";
import 'react-circular-progressbar/dist/styles.css';
import 'bootstrap/dist/css/bootstrap.min.css';
import { useState, useEffect} from 'react';
function App() {
  const [data, setData] = useState();

  useEffect(() => {
    const intervalId = setInterval(() => {  //assign interval to a variable to clear it.
      console.log("perform polling");
      fetch("/pluginfo")
      .then(data => data.json())
      .then(data => {setData(data); console.log(data)})
      .catch(function(error) {
        console.log(error)
      })
    }, 5000)
  
    return () => clearInterval(intervalId); //This is important
   
  }, ["/pluginfo", useState])

  const changeSwitch = (index) => {
    let dataState = Object.create(data);
    let body = `{ "act": "ON", "ip": "${dataState.plugs[index].address}"}`;
    console.log(body);
    if (dataState.plugs[index].status === 1) {
      body = `{ "act": "OFF", "ip": "${dataState.plugs[index].address}"}`;
    }
    fetch('/plugcmd', {
      method: 'POST',
      body: body
    }).then((res) => {
      if (res.status === "200") {
        dataState.plugs[index].status = Math.abs(dataState.plugs[index].status - 1);
        setData(dataState);
      }
    });
  };
  let value = data ? data.currentConsume / data.availableWatts * 100 : 0;
  return (
    <div className="App">
      <div className="col-md-2 offset-md-5 mt-4">
        <h3 className="text-center">Current power usage</h3>
        <CircularProgressbar value={value} text={`${value}%`} />
      </div>
      <div className="row mt-4">
        <div className="col-md-4 offset-md-4">
          {data && data.plugs.map((x, index) => (
            <div className="card">
              <div className="row p-2">
                <div className="col-8">
                  <h4>{x.type}</h4>
                  <span>{x.watts} W</span>
                </div>
                <div className="col-4 align-self-center">
                  <Switch disabled={(data.plugs[index].status === 0 && data.currentConsume + data.plugs[index].max_power_usage > data.totalWattAvailable)} onChange={() => changeSwitch(index)} checked={(x.status === 1)} />
                </div>
              </div></div>))
          }
        </div>
      </div>
    </div>
  );
}

export default App;
