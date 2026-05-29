import { useState, useEffect, useRef, useCallback } from "react";

// ── Design System ──────────────────────────────────────────
// Aesthetic: Swiss Editorial — stark B&W, tight grid, no decoration
// Typography: DM Mono for data, Syne for display
// Language: purely typographic — no icons, no emojis, no flourishes

const ACTIONS = [
  { id:"POWER_OFF",     label:"Power Off",    cat:"device",  danger:"critical", confirm:true },
  { id:"RESTART",       label:"Restart",      cat:"device",  danger:"high",     confirm:true },
  { id:"SLEEP_SCREEN",  label:"Sleep",        cat:"device",  danger:"none" },
  { id:"WAKE_SCREEN",   label:"Wake",         cat:"device",  danger:"none" },
  { id:"FLASHLIGHT_ON", label:"Torch On",     cat:"device",  danger:"none" },
  { id:"FLASHLIGHT_OFF",label:"Torch Off",    cat:"device",  danger:"none" },
  { id:"SCREENSHOT",    label:"Screenshot",   cat:"device",  danger:"none" },
  { id:"ANSWER_CALL",   label:"Answer",       cat:"calls",   danger:"low" },
  { id:"DECLINE_CALL",  label:"Decline",      cat:"calls",   danger:"low" },
  { id:"MAKE_CALL",     label:"Dial",         cat:"calls",   danger:"medium",   confirm:true },
  { id:"END_CALL",      label:"End Call",     cat:"calls",   danger:"low" },
  { id:"MUTE_CALL",     label:"Mute",         cat:"calls",   danger:"none" },
  { id:"UNMUTE_CALL",   label:"Unmute",       cat:"calls",   danger:"none" },
  { id:"SPEAKER_ON",    label:"Speaker",      cat:"calls",   danger:"none" },
  { id:"HOLD_CALL",     label:"Hold",         cat:"calls",   danger:"none" },
  { id:"EMERGENCY",     label:"SOS",          cat:"calls",   danger:"critical" },
  { id:"VOLUME_UP",     label:"Vol +",        cat:"audio",   danger:"none" },
  { id:"VOLUME_DOWN",   label:"Vol −",        cat:"audio",   danger:"none" },
  { id:"MUTE_PHONE",    label:"Mute Phone",   cat:"audio",   danger:"none" },
  { id:"UNMUTE_PHONE",  label:"Unmute Phone", cat:"audio",   danger:"none" },
  { id:"DND",           label:"Do Not Disturb",cat:"audio",  danger:"low" },
  { id:"WIFI_ON",       label:"WiFi On",      cat:"connect", danger:"none" },
  { id:"WIFI_OFF",      label:"WiFi Off",     cat:"connect", danger:"low" },
  { id:"BT_ON",         label:"Bluetooth On", cat:"connect", danger:"none" },
  { id:"BT_OFF",        label:"Bluetooth Off",cat:"connect", danger:"none" },
  { id:"AIRPLANE_ON",   label:"Airplane Mode",cat:"connect", danger:"high",     confirm:true },
  { id:"HOTSPOT_ON",    label:"Hotspot",      cat:"connect", danger:"low" },
  { id:"FOCUS_SLEEP",   label:"Sleep Mode",   cat:"focus",   danger:"none" },
  { id:"FOCUS_FOCUS",   label:"Focus Mode",   cat:"focus",   danger:"none" },
  { id:"FOCUS_DRIVE",   label:"Drive Mode",   cat:"focus",   danger:"none" },
  { id:"FOCUS_MEETING", label:"Meeting",      cat:"focus",   danger:"none" },
  { id:"FOCUS_GYM",     label:"Gym Mode",     cat:"focus",   danger:"none" },
  { id:"FOCUS_OFF",     label:"Mode Off",     cat:"focus",   danger:"none" },
  { id:"SET_ALARM",     label:"Set Alarm",    cat:"utility", danger:"none" },
  { id:"SET_TIMER",     label:"Set Timer",    cat:"utility", danger:"none" },
  { id:"READ_SMS",      label:"Read SMS",     cat:"utility", danger:"low" },
  { id:"READ_NOTIFS",   label:"Notifications",cat:"utility", danger:"none" },
  { id:"BATTERY",       label:"Battery",      cat:"utility", danger:"none" },
  { id:"GO_HOME",       label:"Home",         cat:"nav",     danger:"none" },
  { id:"GO_BACK",       label:"Back",         cat:"nav",     danger:"none" },
];

const CATS = ["all","device","calls","audio","connect","focus","utility","nav"];

const FOCUS_MODES = [
  { id:"NONE",    label:"Normal",  desc:"All calls and notifications active" },
  { id:"SLEEP",   label:"Sleep",   desc:"Full silence. Emergency calls only." },
  { id:"FOCUS",   label:"Focus",   desc:"Notifications silenced. Whitelist calls only." },
  { id:"DRIVE",   label:"Drive",   desc:"Hands-free. Auto-reply SMS enabled." },
  { id:"MEETING", label:"Meeting", desc:"Vibrate only. Whitelist calls only." },
  { id:"GYM",     label:"Gym",     desc:"Media max. All calls silenced." },
];

const MEMORY_DEMO = [
  { role:"user", text:"Call Priya" },
  { role:"ai",   text:'MAKE_CALL — contact="Priya"' },
  { role:"user", text:"Tell her I will be late" },
  { role:"ai",   text:'SEND_SMS — contact="Priya" (resolved via context)' },
  { role:"user", text:"Volume to 50" },
  { role:"ai",   text:"VOLUME_SET — 50%" },
  { role:"user", text:"Increase it a bit" },
  { role:"ai",   text:"VOLUME_SET — 65% (delta from remembered 50%)" },
  { role:"user", text:"Gym mode for an hour" },
  { role:"ai",   text:"FOCUS_MODE GYM — duration 60 min" },
];

// ── Danger indicator ──────────────────────────────────────
function DangerPip({ level }) {
  const map = { none:"#404040", low:"#666", medium:"#999", high:"#ccc", critical:"#fff", emergency:"#fff" };
  const size = level === "critical" || level === "emergency" ? 7 : 5;
  return (
    <span style={{
      display: "inline-block", width: size, height: size, borderRadius: "50%",
      background: map[level] || "#404040",
      boxShadow: (level === "critical" || level === "emergency") ? "0 0 0 1px #fff" : "none",
      flexShrink: 0,
    }} />
  );
}

// ── Action button — pure typographic ─────────────────────
function ActionBtn({ a, onClick }) {
  const [hover, setHover] = useState(false);
  const isDestructive = a.danger === "critical" || a.danger === "high";
  return (
    <button
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onClick={() => onClick(a)}
      style={{
        background: hover ? (isDestructive ? "#fff" : "#181818") : "#000",
        border: `1px solid ${hover ? "#fff" : "#222"}`,
        borderRadius: 0,
        padding: "14px 12px 12px",
        cursor: "pointer",
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: 10,
        transition: "all 0.1s ease",
        textAlign: "left",
        minHeight: 80,
      }}>
      <span style={{
        color: hover && isDestructive ? "#000" : hover ? "#fff" : "#555",
        fontSize: 9,
        fontFamily: "'DM Mono', monospace",
        letterSpacing: "0.12em",
        textTransform: "uppercase",
        lineHeight: 1,
      }}>{a.id}</span>
      <span style={{
        color: hover && isDestructive ? "#000" : hover ? "#fff" : "#e0e0e0",
        fontSize: 12,
        fontFamily: "'Syne', sans-serif",
        fontWeight: 700,
        lineHeight: 1.2,
        flex: 1,
      }}>{a.label}</span>
      <DangerPip level={hover && isDestructive ? "none" : a.danger} />
    </button>
  );
}

// ── Confirm Modal ────────────────────────────────────────
function ConfirmModal({ action, onConfirm, onCancel }) {
  const [sec, setSec] = useState(8);
  useEffect(() => {
    if (sec <= 0) { onCancel(); return; }
    const t = setInterval(() => setSec(s => s - 1), 1000);
    return () => clearInterval(t);
  }, [sec]);
  return (
    <div style={{
      position: "fixed", inset: 0, background: "rgba(0,0,0,0.92)",
      display: "flex", alignItems: "center", justifyContent: "center",
      zIndex: 9999, backdropFilter: "blur(2px)",
    }}>
      <div style={{
        background: "#000", border: "1px solid #fff",
        padding: "40px 48px", width: 420, maxWidth: "90vw",
      }}>
        <div style={{ fontFamily: "'DM Mono', monospace", fontSize: 9, color: "#555", letterSpacing: "0.15em", marginBottom: 28 }}>
          CONFIRMATION REQUIRED
        </div>
        <div style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: 22, color: "#fff", marginBottom: 12, lineHeight: 1.2 }}>
          {action.label}
        </div>
        <div style={{ fontFamily: "'DM Mono', monospace", fontSize: 11, color: "#666", marginBottom: 36, lineHeight: 1.7 }}>
          This action requires explicit confirmation.<br />
          Say "confirm" or press the button below.
        </div>

        {/* Progress bar */}
        <div style={{ background: "#111", height: 2, marginBottom: 10, overflow: "hidden" }}>
          <div style={{ height: "100%", width: `${(sec / 8) * 100}%`, background: "#fff", transition: "width 1s linear" }} />
        </div>
        <div style={{ fontFamily: "'DM Mono', monospace", fontSize: 10, color: "#444", marginBottom: 36 }}>
          AUTO-CANCEL IN {sec}S
        </div>

        <div style={{ display: "flex", gap: 12 }}>
          <button onClick={onCancel} style={{
            flex: 1, padding: "14px", background: "#000", border: "1px solid #333",
            color: "#555", cursor: "pointer", fontFamily: "'DM Mono', monospace",
            fontSize: 11, letterSpacing: "0.1em",
          }}>CANCEL</button>
          <button onClick={onConfirm} style={{
            flex: 1, padding: "14px", background: "#fff", border: "none",
            color: "#000", cursor: "pointer", fontFamily: "'DM Mono', monospace",
            fontSize: 11, fontWeight: 700, letterSpacing: "0.1em",
          }}>CONFIRM</button>
        </div>
      </div>
    </div>
  );
}

// ── Terminal log ─────────────────────────────────────────
function Terminal({ lines }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight; }, [lines]);
  return (
    <div ref={ref} style={{
      background: "#000", borderTop: "1px solid #222",
      padding: "16px 20px", height: 200, overflowY: "auto",
      fontFamily: "'DM Mono', monospace", fontSize: 11,
      lineHeight: 1.8,
    }}>
      {lines.map((l, i) => (
        <div key={i} style={{ display: "flex", gap: 16 }}>
          <span style={{ color: "#333", minWidth: 64, flexShrink: 0 }}>{l.time}</span>
          <span style={{
            color: l.type === "success" ? "#e0e0e0" : l.type === "error" ? "#888" : l.type === "warn" ? "#aaa" : "#555"
          }}>
            {l.type === "error" ? "-- " : l.type === "success" ? "   " : "   "}{l.text}
          </span>
        </div>
      ))}
      <div style={{ color: "#333" }}>_</div>
    </div>
  );
}

// ── Battery bar ───────────────────────────────────────────
function BatteryBar({ level, charging }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <div style={{ width: 32, height: 14, border: "1px solid #444", position: "relative", display: "flex", alignItems: "center" }}>
        <div style={{ position: "absolute", right: -4, top: "50%", transform: "translateY(-50%)", width: 3, height: 6, background: "#444" }} />
        <div style={{ width: `${level}%`, height: "100%", background: level < 20 ? "#888" : "#e0e0e0", transition: "width 0.4s" }} />
      </div>
      <span style={{ fontFamily: "'DM Mono', monospace", fontSize: 10, color: "#555", letterSpacing: "0.05em" }}>
        {level}%{charging ? " CHG" : ""}
      </span>
    </div>
  );
}

// ── Section label ─────────────────────────────────────────
function Label({ children, right }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
      <span style={{ fontFamily: "'DM Mono', monospace", fontSize: 9, color: "#444", letterSpacing: "0.15em", textTransform: "uppercase" }}>{children}</span>
      {right && <span style={{ fontFamily: "'DM Mono', monospace", fontSize: 9, color: "#444" }}>{right}</span>}
    </div>
  );
}

// ── Divider ───────────────────────────────────────────────
const Div = () => <div style={{ height: 1, background: "#111", margin: "32px 0" }} />;

// ══════════════════════════════════════════════════════════
//  MAIN
// ══════════════════════════════════════════════════════════
export default function PhoneAIDashboard() {
  const [tab, setTab]           = useState("controls");
  const [cat, setCat]           = useState("all");
  const [serviceOn, setServiceOn] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [cmd, setCmd]           = useState("");
  const [confirm, setConfirm]   = useState(null);
  const [focusMode, setFocusMode] = useState("NONE");
  const [callActive, setCallActive] = useState(false);
  const [callMuted, setCallMuted] = useState(false);
  const [battery, setBattery]   = useState({ level: 87, charging: true });
  const [volume, setVolume]     = useState(65);
  const [brightness, setBrightness] = useState(80);
  const [callTimer, setCallTimer] = useState(0);
  const [routines, setRoutines] = useState([
    { id:"morning", name:"Good Morning",    time:"07:00", trigger:"Daily",     actions:["Auto brightness","Unmute","Volume 60%"],  enabled:true },
    { id:"bedtime", name:"Bedtime",          time:"22:30", trigger:"Daily",     actions:["Brightness 10%","DND","Volume 20%"],       enabled:true },
    { id:"postcall",name:"Post-Call",        time:"",      trigger:"After Call",actions:["Unmute","Speaker off"],                   enabled:true },
    { id:"weekwork",name:"Work Hours",       time:"09:00", trigger:"Weekday",   actions:["Focus mode","DND on"],                    enabled:false },
  ]);
  const [log, setLog] = useState([
    { ts:Date.now()-600000, action:"VOLUME_UP",    result:"Volume at 70%",      ok:true },
    { ts:Date.now()-540000, action:"ANSWER_CALL",  result:"Call answered",       ok:true },
    { ts:Date.now()-480000, action:"POWER_OFF",    result:"Blocked — cancelled", ok:false },
    { ts:Date.now()-420000, action:"SLEEP_SCREEN", result:"Screen locked",       ok:true },
    { ts:Date.now()-360000, action:"SEND_SMS",     result:"Sent to Priya",       ok:true },
    { ts:Date.now()-300000, action:"FOCUS_DRIVE",  result:"Drive mode active",   ok:true },
    { ts:Date.now()-240000, action:"BATTERY",      result:"87%, charging AC",    ok:true },
  ]);
  const [terminal, setTerminal] = useState([
    { time:"00:00:00", text:"PhoneAI v2 initialized", type:"success" },
    { time:"00:00:01", text:"Groq LLaMA-3.3-70B connected", type:"success" },
    { time:"00:00:01", text:"AccessibilityService active", type:"success" },
    { time:"00:00:01", text:"InCallService active", type:"success" },
    { time:"00:00:02", text:"ShakeDetector armed", type:"success" },
    { time:"00:00:02", text:"BatteryGuardian monitoring", type:"success" },
    { time:"00:00:02", text:"Safety gate armed", type:"success" },
    { time:"00:00:03", text:'Wake word "hey phone" active', type:"default" },
    { time:"00:00:03", text:"Ready", type:"success" },
  ]);

  const timerRef = useRef(null);
  const nowStr = () => new Date().toLocaleTimeString("en-GB", { hour12: false });
  const term = (text, type = "default") => setTerminal(p => [...p.slice(-100), { time: nowStr(), text, type }]);

  useEffect(() => {
    if (callActive) timerRef.current = setInterval(() => setCallTimer(t => t + 1), 1000);
    else { clearInterval(timerRef.current); setCallTimer(0); }
    return () => clearInterval(timerRef.current);
  }, [callActive]);

  useEffect(() => {
    if (!serviceOn) return;
    const t = setInterval(() => setBattery(b => b.charging ? (b.level >= 100 ? b : {...b, level:b.level+1}) : (b.level <= 0 ? b : {...b, level:b.level-1})), 9000);
    return () => clearInterval(t);
  }, [serviceOn]);

  const fmtTime = s => `${String(Math.floor(s/60)).padStart(2,"0")}:${String(s%60).padStart(2,"0")}`;

  const resultFor = (a) => ({
    POWER_OFF:"Power off initiated", RESTART:"Reboot in progress",
    SLEEP_SCREEN:"Screen locked", FLASHLIGHT_ON:"Torch on",
    FLASHLIGHT_OFF:"Torch off", SCREENSHOT:"Screenshot saved",
    ANSWER_CALL:"Call answered", DECLINE_CALL:"Call declined",
    END_CALL:"Call ended", MUTE_CALL:"Muted", UNMUTE_CALL:"Unmuted",
    SPEAKER_ON:"Speaker on", HOLD_CALL:"On hold", MAKE_CALL:"Dialing",
    EMERGENCY:"Calling 112", VOLUME_UP:`${Math.min(volume+10,100)}%`,
    VOLUME_DOWN:`${Math.max(volume-10,0)}%`,
    MUTE_PHONE:"Phone muted", UNMUTE_PHONE:"Phone unmuted",
    DND:"Do not disturb on", WIFI_ON:"WiFi on", WIFI_OFF:"WiFi off",
    BT_ON:"Bluetooth on", BT_OFF:"Bluetooth off", AIRPLANE_ON:"Airplane mode on",
    HOTSPOT_ON:"Hotspot active", FOCUS_SLEEP:"Sleep mode active",
    FOCUS_FOCUS:"Focus mode active", FOCUS_DRIVE:"Drive mode active",
    FOCUS_MEETING:"Meeting mode active", FOCUS_GYM:"Gym mode active",
    FOCUS_OFF:"Mode deactivated", SET_ALARM:"Alarm set", SET_TIMER:"Timer started",
    READ_SMS:"Reading last message", READ_NOTIFS:"3 unread",
    BATTERY:`${battery.level}%, ${battery.charging?"charging":"not charging"}`,
    GO_HOME:"Home", GO_BACK:"Back",
  }[a.id] || "Done");

  const applyState = (a) => {
    if (a.id==="ANSWER_CALL") setCallActive(true);
    if (a.id==="END_CALL"||a.id==="DECLINE_CALL") setCallActive(false);
    if (a.id==="MUTE_CALL") setCallMuted(true);
    if (a.id==="UNMUTE_CALL") setCallMuted(false);
    if (a.id==="VOLUME_UP") setVolume(v=>Math.min(v+10,100));
    if (a.id==="VOLUME_DOWN") setVolume(v=>Math.max(v-10,0));
    if (a.id.startsWith("FOCUS_")) setFocusMode(a.id.replace("FOCUS_",""));
  };

  const execute = useCallback((a) => {
    setProcessing(true);
    term(`${a.id}`, "warn");
    setTimeout(() => {
      const r = resultFor(a);
      term(`${r}`, "success");
      setLog(p => [{ts:Date.now(),action:a.id,result:r,ok:true},...p.slice(0,49)]);
      setProcessing(false);
      applyState(a);
    }, 350 + Math.random()*250);
  }, [volume, battery]);

  const handleAction = useCallback((a) => {
    if (!serviceOn) { term("Service offline", "error"); return; }
    if (a.confirm || a.danger==="critical" || a.danger==="high") {
      setConfirm(a); term(`Confirm required — ${a.id}`, "warn"); return;
    }
    execute(a);
  }, [serviceOn, execute]);

  const handleCmd = () => {
    if (!cmd.trim()||processing) return;
    const text = cmd.trim(); setCmd("");
    term(`> ${text}`, "default");
    setProcessing(true);
    setTimeout(() => {
      const m = ACTIONS.find(a => text.toLowerCase().includes(a.label.toLowerCase()) || text.toLowerCase().includes(a.id.toLowerCase().replace(/_/g," ")));
      if (m) {
        term(`intent:${m.id} conf:0.9${5+Math.floor(Math.random()*4)}`, "default");
        setProcessing(false); handleAction(m);
      } else {
        setTimeout(() => { term("I can control calls, device, focus modes and more.", "success"); setProcessing(false); }, 500);
      }
    }, 700);
  };

  const filtered = ACTIONS.filter(a => cat==="all"||a.cat===cat);
  const activeFocus = FOCUS_MODES.find(f=>f.id===focusMode)||FOCUS_MODES[0];

  // ── Tab nav ────────────────────────────────────────────
  const tabs = [["controls","Controls"],["focus","Focus"],["memory","Memory"],["routines","Routines"],["log","Log"],["docs","Reference"]];

  return (
    <div style={{ minHeight:"100vh", background:"#000", color:"#e0e0e0", fontFamily:"'DM Mono',monospace" }}>

      {/* ─── FONTS ─── */}
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@300;400;500&family=Syne:wght@700;800&display=swap');
        *, *::before, *::after { box-sizing:border-box; margin:0; padding:0; }
        body { background:#000; }
        ::-webkit-scrollbar { width:4px; height:4px; }
        ::-webkit-scrollbar-track { background:#000; }
        ::-webkit-scrollbar-thumb { background:#222; }
        input::placeholder { color:#333; }
        input[type=range] { -webkit-appearance:none; height:1px; background:#222; cursor:pointer; outline:none; }
        input[type=range]::-webkit-slider-thumb { -webkit-appearance:none; width:12px; height:12px; background:#fff; border-radius:0; cursor:pointer; }
        button { font-family:'DM Mono',monospace; }
        @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
        @keyframes fadeIn { from{opacity:0;transform:translateY(6px)} to{opacity:1;transform:translateY(0)} }
      `}</style>

      {/* ─── HEADER ─── */}
      <header style={{
        height: 56, borderBottom:"1px solid #1a1a1a",
        display:"flex", alignItems:"center",
        padding:"0 28px", gap:28,
        position:"sticky", top:0, zIndex:200, background:"#000",
      }}>
        <div style={{ display:"flex", alignItems:"baseline", gap:10 }}>
          <span style={{ fontFamily:"'Syne',sans-serif", fontWeight:800, fontSize:16, color:"#fff", letterSpacing:"-0.02em" }}>PhoneAI</span>
          <span style={{ fontSize:9, color:"#333", letterSpacing:"0.1em" }}>v2.0</span>
        </div>

        <div style={{ width:1, height:20, background:"#1a1a1a" }} />

        {/* Status */}
        <div style={{ display:"flex", alignItems:"center", gap:8 }}>
          <span style={{ width:6, height:6, borderRadius:"50%", background: serviceOn?"#fff":"#333", display:"inline-block", animation: serviceOn?"blink 2s infinite":"none" }} />
          <span style={{ fontSize:9, color:"#444", letterSpacing:"0.12em" }}>{serviceOn?"LIVE":"OFFLINE"}</span>
        </div>

        <div style={{ width:1, height:20, background:"#1a1a1a" }} />

        {/* Focus mode */}
        <span style={{ fontSize:9, color:"#444", letterSpacing:"0.1em" }}>
          {activeFocus.id === "NONE" ? "NO MODE" : activeFocus.label.toUpperCase()}
        </span>

        <div style={{ flex:1 }} />

        {/* Battery */}
        <BatteryBar level={battery.level} charging={battery.charging} />

        <div style={{ width:1, height:20, background:"#1a1a1a" }} />

        {/* Volume */}
        <span style={{ fontSize:9, color:"#444", letterSpacing:"0.1em" }}>VOL {volume}%</span>

        <div style={{ width:1, height:20, background:"#1a1a1a" }} />

        {/* Toggle */}
        <button onClick={() => { setServiceOn(s=>!s); term(serviceOn?"Service stopped":"Service started", serviceOn?"error":"success"); }}
          style={{ padding:"6px 14px", background: serviceOn?"#1a1a1a":"#fff", border:`1px solid ${serviceOn?"#2a2a2a":"#fff"}`, color:serviceOn?"#888":"#000", cursor:"pointer", fontSize:9, letterSpacing:"0.12em" }}>
          {serviceOn?"STOP":"START"}
        </button>
      </header>

      {/* ─── CALL BANNER ─── */}
      {callActive && (
        <div style={{ borderBottom:"1px solid #222", padding:"12px 28px", display:"flex", alignItems:"center", gap:24, animation:"fadeIn 0.2s ease" }}>
          <div>
            <div style={{ fontSize:9, color:"#555", letterSpacing:"0.12em", marginBottom:3 }}>ACTIVE CALL</div>
            <div style={{ fontFamily:"'Syne',sans-serif", fontWeight:700, fontSize:14, color:"#fff" }}>{fmtTime(callTimer)}</div>
          </div>
          <div style={{ width:1, height:32, background:"#1a1a1a" }} />
          <span style={{ fontSize:10, color:"#555", letterSpacing:"0.05em" }}>+91 98765 43210</span>
          <div style={{ flex:1 }} />
          {[
            { id:"MUTE_CALL",   label: callMuted?"UNMUTE":"MUTE" },
            { id:"SPEAKER_ON",  label:"SPEAKER" },
            { id:"HOLD_CALL",   label:"HOLD" },
            { id:"END_CALL",    label:"END",  end:true },
          ].map(b => (
            <button key={b.id}
              onClick={() => handleAction(ACTIONS.find(a=>a.id===b.id)||{id:b.id,label:b.label,cat:"calls",danger:"none"})}
              style={{ padding:"6px 14px", background:b.end?"#fff":"#000", border:`1px solid ${b.end?"#fff":"#2a2a2a"}`, color:b.end?"#000":"#666", cursor:"pointer", fontSize:9, letterSpacing:"0.12em" }}>
              {b.label}
            </button>
          ))}
        </div>
      )}

      {/* ─── COMMAND INPUT ─── */}
      <div style={{ padding:"20px 28px", borderBottom:"1px solid #111" }}>
        <div style={{ display:"flex", gap:0, border:"1px solid #1a1a1a" }}>
          <span style={{ padding:"0 14px", display:"flex", alignItems:"center", borderRight:"1px solid #1a1a1a" }}>
            <span style={{ fontSize:9, color:"#333", letterSpacing:"0.12em" }}>INPUT</span>
          </span>
          <input value={cmd} onChange={e=>setCmd(e.target.value)} onKeyDown={e=>e.key==="Enter"&&handleCmd()}
            placeholder='type a command or ask anything — "call Priya"  "sleep mode"  "battery"'
            style={{ flex:1, background:"#000", border:"none", outline:"none", color:"#e0e0e0", fontSize:11, padding:"14px 16px", fontFamily:"'DM Mono',monospace" }} />
          {processing && (
            <div style={{ padding:"0 16px", display:"flex", alignItems:"center", gap:4, borderLeft:"1px solid #1a1a1a" }}>
              {[0,1,2].map(i=><span key={i} style={{ width:3, height:3, background:"#444", borderRadius:"50%", animation:`blink 0.8s ${i*0.2}s infinite` }} />)}
            </div>
          )}
          <button onClick={handleCmd} disabled={processing||!cmd.trim()}
            style={{ padding:"0 20px", background: (processing||!cmd.trim())?"#000":"#fff", border:"none", borderLeft:"1px solid #1a1a1a", color:(processing||!cmd.trim())?"#333":"#000", cursor:(processing||!cmd.trim())?"not-allowed":"pointer", fontSize:9, letterSpacing:"0.12em" }}>
            SEND
          </button>
        </div>
      </div>

      {/* ─── TABS ─── */}
      <div style={{ display:"flex", borderBottom:"1px solid #111", padding:"0 28px", overflowX:"auto" }}>
        {tabs.map(([id,label])=>(
          <button key={id} onClick={()=>setTab(id)} style={{
            padding:"14px 0", marginRight:28, background:"transparent", border:"none",
            borderBottom: tab===id?"1px solid #fff":"1px solid transparent",
            color: tab===id?"#fff":"#444", cursor:"pointer", fontSize:9,
            letterSpacing:"0.12em", textTransform:"uppercase", whiteSpace:"nowrap",
          }}>{label}</button>
        ))}
      </div>

      {/* ─── CONTENT ─── */}
      <main style={{ padding:"28px", maxWidth:1200, margin:"0 auto" }}>

        {/* ════════════ CONTROLS ════════════ */}
        {tab==="controls" && (
          <>
            {/* Category filter */}
            <Label>Category</Label>
            <div style={{ display:"flex", gap:2, marginBottom:28, flexWrap:"wrap" }}>
              {CATS.map(c=>(
                <button key={c} onClick={()=>setCat(c)} style={{
                  padding:"5px 14px", background:cat===c?"#fff":"#000",
                  border:`1px solid ${cat===c?"#fff":"#222"}`,
                  color:cat===c?"#000":"#444", cursor:"pointer", fontSize:9,
                  letterSpacing:"0.12em", textTransform:"uppercase",
                  transition:"all 0.1s",
                }}>{c}</button>
              ))}
            </div>

            {/* Grid */}
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(110px,1fr))", gap:1, marginBottom:28, background:"#111" }}>
              {filtered.map(a=>(
                <div key={a.id} style={{ background:"#000" }}>
                  <ActionBtn a={a} onClick={handleAction} />
                </div>
              ))}
            </div>

            {/* Sliders */}
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:1, marginBottom:28, background:"#111" }}>
              {[
                { label:"Volume", key:"volume", value:volume, set:setVolume },
                { label:"Brightness", key:"brightness", value:brightness, set:setBrightness },
              ].map(s=>(
                <div key={s.key} style={{ background:"#000", padding:"20px 20px 18px" }}>
                  <div style={{ display:"flex", justifyContent:"space-between", marginBottom:14 }}>
                    <span style={{ fontSize:9, color:"#444", letterSpacing:"0.12em" }}>{s.label.toUpperCase()}</span>
                    <span style={{ fontSize:9, color:"#666" }}>{s.value}%</span>
                  </div>
                  <input type="range" min="0" max="100" value={s.value} onChange={e=>s.set(Number(e.target.value))} style={{ width:"100%" }} />
                </div>
              ))}
            </div>

            {/* Danger legend */}
            <div style={{ display:"flex", gap:24, alignItems:"center", marginBottom:28, padding:"14px 0", borderTop:"1px solid #111", borderBottom:"1px solid #111" }}>
              <span style={{ fontSize:9, color:"#2a2a2a", letterSpacing:"0.12em" }}>DANGER</span>
              {Object.entries({ none:"#404040", low:"#666", medium:"#999", high:"#ccc", critical:"#fff" }).map(([k,v])=>(
                <div key={k} style={{ display:"flex", alignItems:"center", gap:7 }}>
                  <span style={{ width:5, height:5, borderRadius:"50%", background:v, display:"inline-block" }} />
                  <span style={{ fontSize:9, color:"#333", letterSpacing:"0.1em", textTransform:"uppercase" }}>{k}</span>
                </div>
              ))}
            </div>

            {/* Terminal */}
            <Label>System Log</Label>
            <Terminal lines={terminal} />
          </>
        )}

        {/* ════════════ FOCUS ════════════ */}
        {tab==="focus" && (
          <>
            <Label right={`Active — ${activeFocus.label.toUpperCase()}`}>Focus Modes</Label>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:1, background:"#111", marginBottom:28 }}>
              {FOCUS_MODES.map(mode=>(
                <button key={mode.id} onClick={()=>{ setFocusMode(mode.id); term(`${mode.id} activated`, "success"); }}
                  style={{
                    background: focusMode===mode.id?"#fff":"#000",
                    border:"none", padding:"24px 20px", cursor:"pointer", textAlign:"left",
                    transition:"background 0.1s",
                  }}>
                  <div style={{ fontFamily:"'Syne',sans-serif", fontWeight:800, fontSize:15, color:focusMode===mode.id?"#000":"#e0e0e0", marginBottom:8 }}>{mode.label}</div>
                  <div style={{ fontSize:9, color:focusMode===mode.id?"#555":"#444", lineHeight:1.6, letterSpacing:"0.03em", fontFamily:"'DM Mono',monospace" }}>{mode.desc}</div>
                  {focusMode===mode.id && <div style={{ marginTop:12, fontSize:9, color:"#000", letterSpacing:"0.12em" }}>ACTIVE</div>}
                </button>
              ))}
            </div>

            <Label>Mode Configuration</Label>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:1, background:"#111" }}>
              {[
                { k:"Ringer",     v: focusMode==="GYM"||focusMode==="SLEEP"?"Silent":focusMode==="FOCUS"||focusMode==="MEETING"?"Vibrate":"Normal" },
                { k:"DND",        v: focusMode==="NONE"?"Off":"On" },
                { k:"Auto-Reply", v: focusMode==="NONE"?"None":"Enabled" },
                { k:"Calls From", v: focusMode==="SLEEP"||focusMode==="FOCUS"||focusMode==="MEETING"?"Whitelist":focusMode==="GYM"?"None":"All" },
                { k:"Media Volume",v: focusMode==="GYM"?"100%":focusMode==="SLEEP"?"0%":focusMode==="FOCUS"?"30%":"Normal" },
                { k:"Status",     v: focusMode==="NONE"?"Inactive":"Active" },
              ].map((item,i)=>(
                <div key={i} style={{ background:"#000", padding:"20px" }}>
                  <div style={{ fontSize:9, color:"#333", letterSpacing:"0.12em", marginBottom:8 }}>{item.k.toUpperCase()}</div>
                  <div style={{ fontFamily:"'Syne',sans-serif", fontWeight:700, fontSize:14, color:"#e0e0e0" }}>{item.v}</div>
                </div>
              ))}
            </div>
          </>
        )}

        {/* ════════════ MEMORY ════════════ */}
        {tab==="memory" && (
          <>
            <Label>Conversation Context Window</Label>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:28 }}>
              <div>
                <div style={{ border:"1px solid #111" }}>
                  {MEMORY_DEMO.map((turn,i)=>(
                    <div key={i} style={{ padding:"14px 18px", borderBottom: i<MEMORY_DEMO.length-1?"1px solid #0a0a0a":"none", display:"flex", gap:16, alignItems:"flex-start" }}>
                      <span style={{ fontSize:9, color: turn.role==="user"?"#555":"#2a2a2a", letterSpacing:"0.12em", minWidth:28, paddingTop:1 }}>
                        {turn.role==="user"?"USR":"SYS"}
                      </span>
                      <span style={{ fontSize:11, color: turn.role==="user"?"#e0e0e0":"#555", lineHeight:1.5 }}>{turn.text}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <Label>Resolved State</Label>
                <div style={{ border:"1px solid #111" }}>
                  {[
                    { k:"Last Contact",   v:"Priya" },
                    { k:"Last App",       v:"Spotify" },
                    { k:"Last Volume",    v:"65%" },
                    { k:"Focus Mode",     v:activeFocus.label },
                    { k:"Turns in window",v:"5 / 10" },
                    { k:"Pronoun Map",    v:'"her" -> Priya' },
                  ].map((row,i,arr)=>(
                    <div key={i} style={{ display:"flex", padding:"13px 18px", borderBottom:i<arr.length-1?"1px solid #0a0a0a":"none" }}>
                      <span style={{ fontSize:9, color:"#333", letterSpacing:"0.08em", minWidth:140 }}>{row.k.toUpperCase()}</span>
                      <span style={{ fontSize:11, color:"#e0e0e0" }}>{row.v}</span>
                    </div>
                  ))}
                </div>

                <div style={{ marginTop:20, padding:"18px", border:"1px solid #111" }}>
                  <div style={{ fontSize:9, color:"#2a2a2a", letterSpacing:"0.12em", marginBottom:12 }}>RESOLUTION EXAMPLES</div>
                  {['"call him" -> "call Rahul"','"send her update" -> SMS Priya','"increase it" -> vol +10% from 65%'].map((ex,i)=>(
                    <div key={i} style={{ fontSize:10, color:"#444", lineHeight:2, borderBottom:i<2?"1px solid #0a0a0a":"none", padding:"4px 0" }}>{ex}</div>
                  ))}
                </div>
              </div>
            </div>
          </>
        )}

        {/* ════════════ ROUTINES ════════════ */}
        {tab==="routines" && (
          <>
            <Label right={`${routines.filter(r=>r.enabled).length} active`}>Scheduled Routines</Label>
            <div style={{ border:"1px solid #111" }}>
              {routines.map((r,i)=>(
                <div key={r.id} style={{ display:"flex", alignItems:"center", gap:0, borderBottom:i<routines.length-1?"1px solid #0a0a0a":"none", opacity:r.enabled?1:0.35 }}>
                  <div style={{ width:3, alignSelf:"stretch", background:r.enabled?"#e0e0e0":"#111" }} />
                  <div style={{ flex:1, padding:"18px 20px" }}>
                    <div style={{ display:"flex", alignItems:"baseline", gap:16, marginBottom:6 }}>
                      <span style={{ fontFamily:"'Syne',sans-serif", fontWeight:700, fontSize:13, color:"#e0e0e0" }}>{r.name}</span>
                      <span style={{ fontSize:9, color:"#333", letterSpacing:"0.08em" }}>{r.trigger}{r.time?` — ${r.time}`:""}</span>
                    </div>
                    <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
                      {r.actions.map((a,j)=>(
                        <span key={j} style={{ fontSize:9, color:"#444", border:"1px solid #1a1a1a", padding:"2px 8px", letterSpacing:"0.05em" }}>{a}</span>
                      ))}
                    </div>
                  </div>
                  <button onClick={()=>setRoutines(p=>p.map(x=>x.id===r.id?{...x,enabled:!x.enabled}:x))}
                    style={{ padding:"0 24px", alignSelf:"stretch", background:"transparent", border:"none", borderLeft:"1px solid #111", color:r.enabled?"#e0e0e0":"#333", cursor:"pointer", fontSize:9, letterSpacing:"0.12em" }}>
                    {r.enabled?"ON":"OFF"}
                  </button>
                </div>
              ))}
            </div>

            <div style={{ marginTop:20, padding:"18px 20px", border:"1px solid #111" }}>
              <div style={{ fontSize:9, color:"#2a2a2a", letterSpacing:"0.12em", marginBottom:12 }}>VOICE SYNTAX</div>
              {[
                '"every morning at 7 mute phone"',
                '"at 10pm enable dnd"',
                '"weekdays at 9 focus mode"',
              ].map((ex,i)=>(
                <div key={i} style={{ fontSize:10, color:"#333", lineHeight:2.2 }}>{ex}</div>
              ))}
            </div>
          </>
        )}

        {/* ════════════ LOG ════════════ */}
        {tab==="log" && (
          <>
            <Label right={`${log.length} entries`}>Audit Log</Label>
            <div style={{ display:"flex", justifyContent:"flex-end", marginBottom:16 }}>
              <button onClick={()=>setLog([])} style={{ padding:"5px 14px", background:"#000", border:"1px solid #222", color:"#444", cursor:"pointer", fontSize:9, letterSpacing:"0.12em" }}>CLEAR</button>
            </div>
            <div style={{ border:"1px solid #111" }}>
              {log.length===0 && <div style={{ padding:"32px 20px", fontSize:10, color:"#2a2a2a", textAlign:"center", letterSpacing:"0.1em" }}>NO ENTRIES</div>}
              {log.map((e,i)=>(
                <div key={i} style={{ display:"flex", alignItems:"center", gap:0, borderBottom:i<log.length-1?"1px solid #0a0a0a":"none" }}>
                  <div style={{ width:2, alignSelf:"stretch", background:e.ok?"#e0e0e0":"#333" }} />
                  <div style={{ flex:1, display:"flex", alignItems:"center", gap:24, padding:"13px 18px" }}>
                    <span style={{ fontSize:9, color:"#333", minWidth:60, flexShrink:0 }}>{new Date(e.ts).toLocaleTimeString("en-GB",{hour12:false})}</span>
                    <span style={{ fontSize:9, color:"#888", minWidth:140, letterSpacing:"0.05em", flexShrink:0 }}>{e.action}</span>
                    <span style={{ fontSize:10, color:e.ok?"#e0e0e0":"#444", flex:1 }}>{e.result}</span>
                    <span style={{ fontSize:9, color:e.ok?"#555":"#2a2a2a", letterSpacing:"0.1em" }}>{e.ok?"OK":"BLOCKED"}</span>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {/* ════════════ DOCS ════════════ */}
        {tab==="docs" && (
          <>
            <Label>System Reference</Label>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:1, background:"#111" }}>
              {[
                { title:"Wake Word + Gestures", items:['Default wake word: "Hey Phone"',"Single shake — wake assistant","Double shake — emergency dial 112","Face-down — silence incoming call","All gestures active on lock screen"] },
                { title:"Safety Gate",          items:["Critical actions require voice or tap confirm","8-second auto-cancel countdown","Rate limit: 20 actions per minute","Minimum confidence threshold: 0.5","Emergency calls bypass all safety gates"] },
                { title:"Call Management",      items:["InCallService — full call lifecycle control","CallScreeningService — spam blocking","Whitelist auto-answer for saved contacts","Auto-reply SMS active in focus modes","Caller name announced via TTS on ring"] },
                { title:"AI Memory",            items:["10-turn sliding conversation window","Pronoun resolution — him, her, them","Relative references — increase it, again","Groq LLaMA-3.3-70B intent parsing","Temperature 0.1 for deterministic JSON output"] },
                { title:"Focus Modes",          items:["Modes: Sleep, Focus, Drive, Meeting, Gym","Per-mode ringer, DND, and call filter","Auto-reply SMS active in all non-normal modes","Duration-based deactivation via AlarmManager","Scheduling via voice command"] },
                { title:"Battery Guardian",     items:["Alerts at 20%, 10%, and 5% thresholds","Charging complete announcement","Overheat warning above 42 degrees C","Auto battery-saver option at 10%","Voice query: what is my battery"] },
                { title:"System Services",      items:["ForegroundService — START_STICKY","AccessibilityService — UI + power + nav","NotificationListenerService — read + dismiss","DeviceAdminReceiver — screen lock","BootReceiver — auto-start after reboot"] },
                { title:"Audit Log",            items:["Every action logged with timestamp","Maximum 500 entries, local JSON file","Captures action, parameters, result","Blocked and rate-limited actions logged","Clearable from Settings at any time"] },
              ].map((card,i)=>(
                <div key={i} style={{ background:"#000", padding:"24px 22px" }}>
                  <div style={{ fontFamily:"'Syne',sans-serif", fontWeight:700, fontSize:13, color:"#e0e0e0", marginBottom:16 }}>{card.title}</div>
                  {card.items.map((item,j)=>(
                    <div key={j} style={{ display:"flex", gap:12, padding:"7px 0", borderBottom:j<card.items.length-1?"1px solid #0a0a0a":"none" }}>
                      <span style={{ color:"#2a2a2a", fontSize:10, flexShrink:0 }}>—</span>
                      <span style={{ fontSize:10, color:"#555", lineHeight:1.5 }}>{item}</span>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </>
        )}

      </main>

      {/* ─── CONFIRM MODAL ─── */}
      {confirm && (
        <ConfirmModal action={confirm}
          onConfirm={()=>{ term(`Confirmed — ${confirm.id}`, "success"); execute(confirm); setConfirm(null); }}
          onCancel={()=>{ term(`Cancelled — ${confirm.id}`, "error"); setLog(p=>[{ts:Date.now(),action:confirm.id,result:"Blocked — user cancelled",ok:false},...p.slice(0,49)]); setConfirm(null); }} />
      )}
    </div>
  );
}
