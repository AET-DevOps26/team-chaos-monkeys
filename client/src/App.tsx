import { useEffect, useState } from "react";

function App() {
  const [message, setMessage] = useState<string>("Loading...");

  useEffect(() => {
    fetch("http://localhost:8081/greet")
      .then((res) => res.text())
      .then((text: string) => setMessage(text))
      .catch(() => setMessage("Backend not reachable"));
  }, []);

  return (
    <main style={{ padding: "2rem", fontFamily: "Arial" }}>
      <h1>FoundFlow</h1>
      <p>{message}</p>
    </main>
  );
}

export default App;