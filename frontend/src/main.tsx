import React, { useState, useEffect, useCallback } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  Search,
  LayoutDashboard,
  Boxes,
  Braces,
  LogOut,
  Plus,
  RefreshCw,
  ArrowUpRight,
  GripHorizontal,
  Trash2,
  ChevronRight,
  Clock,
  Shield,
  Radio,
} from "lucide-react";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  BarChart,
  Bar,
} from "recharts";
import GridLayout, { WidthProvider, Layout } from "react-grid-layout";
import "react-grid-layout/css/styles.css";
import "react-resizable/css/styles.css";
import "./style.css";
const Grid = WidthProvider(GridLayout);
type Obj = Record<string, any>;
let token = sessionStorage.getItem("observatory-token") || "";
async function api(path: string, body?: unknown, method?: string) {
  const r = await fetch("/api" + path, {
    method: method || (body ? "POST" : "GET"),
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (r.status === 401 && path != "/auth/login") {
    token = "";
    sessionStorage.removeItem("observatory-token");
    window.dispatchEvent(new Event("auth-expired"));
  }
  const text = await r.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!r.ok) throw Error(data?.error || `Request failed (${r.status})`);
  return data;
}
const errorText = (e: unknown) =>
  e instanceof Error ? e.message : "Request failed";
function Notice({ error }: { error: string }) {
  return error ? (
    <div className="notice" role="alert">
      {error}
    </div>
  ) : null;
}
function Empty({ text = "No events in this time range." }: { text?: string }) {
  return (
    <div className="empty">
      <Activity size={28} />
      <p>{text}</p>
      <small>Adjust your filters or send events with the logging SDK.</small>
    </div>
  );
}
function EventTable({ rows }: { rows: Obj[] }) {
  const [open, setOpen] = useState("");
  return rows.length ? (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Occurred at</th>
            <th>Level</th>
            <th>Application / event</th>
            <th>Message</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <React.Fragment key={r.eventId}>
              <tr
                className="event-row"
                onClick={() => setOpen(open === r.eventId ? "" : r.eventId)}
                tabIndex={0}
                onKeyDown={(e) =>
                  e.key === "Enter" &&
                  setOpen(open === r.eventId ? "" : r.eventId)
                }
              >
                <td className="mono muted">
                  {new Date(r.occurredAt).toLocaleTimeString()}
                </td>
                <td>
                  <span className={"badge " + r.level?.toLowerCase()}>
                    {r.level}
                  </span>
                </td>
                <td>
                  <b>{r.applicationId}</b>
                  <small>{r.eventType}</small>
                </td>
                <td>{r.message}</td>
                <td>
                  <ChevronRight size={14} />
                </td>
              </tr>
              {open === r.eventId && (
                <tr>
                  <td colSpan={5}>
                    <pre>{JSON.stringify(r, null, 2)}</pre>
                  </td>
                </tr>
              )}
            </React.Fragment>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <Empty />
  );
}
function Login({ done }: { done: () => void }) {
  const [email, setEmail] = useState("demo-admin@localhost"),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <div className="login">
      <div className="login-art">
        <div className="brand">
          <Activity /> observatory<span>•</span>
        </div>
        <h1>
          Every event.
          <br />A clearer picture.
        </h1>
        <p>
          Follow your applications from the first signal
          <br />
          to the full story.
        </p>
        <div className="orbit">
          <Activity size={90} />
        </div>
        <small>EVENT INTELLIGENCE / DEVELOPMENT WORKSPACE</small>
      </div>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            const d = await api("/auth/login", { email, password });
            token = d.token;
            sessionStorage.setItem("observatory-token", token);
            done();
          } catch (e) {
            setError(errorText(e));
          } finally {
            setBusy(false);
          }
        }}
      >
        <span className="eyebrow">WELCOME BACK</span>
        <h2>Sign in to your workspace</h2>
        <p className="muted">Your applications, connected.</p>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
        </label>
        <Notice error={error} />
        <button className="primary" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
          <ArrowUpRight size={16} />
        </button>
        <small className="muted">
          Use the development credentials generated in your local .env file.
        </small>
      </form>
    </div>
  );
}
function Explorer({ minutes, tick }: { minutes: number; tick: number }) {
  const [filters, setFilters] = useState<Obj>({}),
    [rows, setRows] = useState<Obj[]>([]),
    [cursor, setCursor] = useState(""),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(false),
    [payload, setPayload] = useState("[]");
  const [bounds, setBounds] = useState({
    from: new Date(Date.now() - minutes * 60000).toISOString(),
    to: new Date().toISOString(),
  });
  const load = async (next = false) => {
    setLoading(true);
    setError("");
    try {
      const range = next
        ? bounds
        : {
            from: new Date(Date.now() - minutes * 60000).toISOString(),
            to: new Date().toISOString(),
          };
      if (!next) setBounds(range);
      const d = await api("/query/events", {
        ...range,
        ...Object.fromEntries(
          Object.entries(filters).filter(([, v]) => v !== ""),
        ),
        filters: JSON.parse(payload),
        limit: 50,
        ...(next ? { cursor } : {}),
      });
      setRows(next ? [...rows, ...d.events] : d.events);
      setCursor(d.nextCursor);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, [minutes, tick]);
  return (
    <>
      <div className="section-heading">
        <div>
          <h2>Event explorer</h2>
          <p>Search the signals behind your applications.</p>
        </div>
        <span className="chip">
          <span className="dot" /> occurredAt timeline
        </span>
      </div>
      <div className="card filters">
        <div className="filter-grid">
          {[
            "applicationId",
            "environment",
            "level",
            "eventType",
            "traceId",
            "schemaVersion",
          ].map((f) => (
            <label key={f}>
              {f.replace(/([A-Z])/g, " $1")}
              <input
                placeholder={
                  f === "level"
                    ? "INFO, WARN, ERROR"
                    : f === "traceId"
                      ? "Find a trace…"
                      : "All"
                }
                value={filters[f] || ""}
                onChange={(e) =>
                  setFilters({
                    ...filters,
                    [f]:
                      f === "schemaVersion" && e.target.value
                        ? Number(e.target.value)
                        : e.target.value,
                  })
                }
              />
            </label>
          ))}
        </div>
        <details>
          <summary>Custom payload filters</summary>
          <p className="muted">
            Use schema fields marked x-queryable. Select event type and schema
            version.
          </p>
          <textarea
            aria-label="Payload filters"
            value={payload}
            onChange={(e) => setPayload(e.target.value)}
            placeholder={'[{"field":"payload.amount","op":"gte","value":100}]'}
          />
        </details>
        <button className="primary" onClick={() => load()} disabled={loading}>
          <Search size={15} /> {loading ? "Searching…" : "Run query"}
        </button>
      </div>
      <Notice error={error} />
      <div className="card">
        <div className="card-title">
          <h3>
            Events <span className="muted">/ {rows.length}</span>
          </h3>
          <span className="muted">Newest first · click a row to inspect</span>
        </div>
        <EventTable rows={rows} />
        {cursor && (
          <button
            className="load-more"
            onClick={() => load(true)}
            disabled={loading}
          >
            Load more events
          </button>
        )}
      </div>
    </>
  );
}
const starterPanels = (): Obj[] => [
  {
    i: crypto.randomUUID(),
    title: "Total events",
    type: "count",
    x: 0,
    y: 0,
    w: 4,
    h: 2,
    query: {},
  },
  {
    i: crypto.randomUUID(),
    title: "Event volume",
    type: "timeseries",
    x: 4,
    y: 0,
    w: 8,
    h: 2,
    query: {},
  },
  {
    i: crypto.randomUUID(),
    title: "Events by level",
    type: "bar",
    x: 0,
    y: 2,
    w: 4,
    h: 3,
    query: { field: "level" },
  },
  {
    i: crypto.randomUUID(),
    title: "Recent events",
    type: "logs",
    x: 4,
    y: 2,
    w: 8,
    h: 3,
    query: {},
  },
];
function Panel({
  p,
  minutes,
  tick,
}: {
  p: Obj;
  minutes: number;
  tick: number;
}) {
  const [data, setData] = useState<Obj[] | null>(null),
    [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    setError("");
    const req = {
      ...p.query,
      from: new Date(Date.now() - minutes * 60000).toISOString(),
      to: new Date().toISOString(),
      limit: 20,
      metric: p.type === "bar" ? "group" : p.type,
      bucketMinutes: Math.max(1, Math.ceil(minutes / 100)),
    };
    api(p.type === "logs" ? "/query/events" : "/query/aggregate", req)
      .then((d) => live && setData(p.type === "logs" ? d.events : d))
      .catch((e) => live && setError(errorText(e)));
    return () => {
      live = false;
    };
  }, [JSON.stringify(p.query), p.type, minutes, tick]);
  if (error) return <Notice error={error} />;
  if (data === null) return <div className="empty">Loading signals…</div>;
  if (p.type === "logs") return <EventTable rows={data} />;
  if (["count", "sum", "avg"].includes(p.type))
    return (
      <div className="stat">
        <span>
          {Number(data[0]?.value || 0).toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}
        </span>
        <small>
          {p.type === "count"
            ? "events observed"
            : `${p.type === "sum" ? "Total" : "Average"} · ${p.query.field || "select a numeric field"}`}
        </small>
        <div className="stat-line" />
      </div>
    );
  if (!data.length) return <Empty />;
  const chart = data.map((r) => ({
    name:
      p.type === "timeseries"
        ? new Date(r._id).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })
        : String(r._id ?? "Unknown"),
    value: r.value,
  }));
  return (
    <div className="chart">
      <ResponsiveContainer width="100%" height="100%">
        {p.type === "timeseries" ? (
          <AreaChart data={chart}>
            <defs>
              <linearGradient id={"fill-" + p.i} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#8b80f9" stopOpacity={0.4} />
                <stop offset="100%" stopColor="#8b80f9" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#252c39" vertical={false} />
            <XAxis
              dataKey="name"
              tick={{ fill: "#8994a9", fontSize: 10 }}
              minTickGap={50}
            />
            <YAxis width={32} tick={{ fill: "#8994a9", fontSize: 10 }} />
            <Tooltip
              contentStyle={{
                background: "#171e2b",
                border: "1px solid #343e50",
              }}
            />
            <Area
              isAnimationActive={false}
              type="monotone"
              dataKey="value"
              stroke="#a59bff"
              fill={"url(#fill-" + p.i + ")"}
              strokeWidth={2}
            />
          </AreaChart>
        ) : (
          <BarChart data={chart}>
            <CartesianGrid stroke="#252c39" vertical={false} />
            <XAxis dataKey="name" tick={{ fill: "#8994a9", fontSize: 10 }} />
            <YAxis width={32} tick={{ fill: "#8994a9", fontSize: 10 }} />
            <Tooltip
              contentStyle={{
                background: "#171e2b",
                border: "1px solid #343e50",
              }}
            />
            <Bar
              isAnimationActive={false}
              dataKey="value"
              fill="#8b80f9"
              radius={[4, 4, 0, 0]}
            />
          </BarChart>
        )}
      </ResponsiveContainer>
    </div>
  );
}
function Dashboards({
  admin,
  minutes,
  tick,
  setMinutes,
}: {
  admin: boolean;
  minutes: number;
  tick: number;
  setMinutes: (v: number) => void;
}) {
  const [list, setList] = useState<Obj[]>([]),
    [current, setCurrent] = useState<Obj | null>(null),
    [editing, setEditing] = useState(false),
    [error, setError] = useState(""),
    [selected, setSelected] = useState<string | null>(null),
    [queryText, setQueryText] = useState("{}"),
    [localTick, setLocalTick] = useState(0),
    [loading, setLoading] = useState(true);
  const load = () =>
    api("/dashboards")
      .then(setList)
      .catch((e) => setError(errorText(e)))
      .finally(() => setLoading(false));
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    if (!current) return;
    const id = setInterval(
      () => setLocalTick((v) => v + 1),
      Math.max(5, current.definition.refreshSeconds) * 1000,
    );
    return () => clearInterval(id);
  }, [current?.dashboardId, current?.definition.refreshSeconds]);
  const def = current?.definition;
  const change = (patch: Obj) =>
    setCurrent((c) =>
      c ? { ...c, definition: { ...c.definition, ...patch } } : c,
    );
  const updatePanel = (id: string, patch: Obj) =>
    change({
      panels: def.panels.map((p: Obj) => (p.i === id ? { ...p, ...patch } : p)),
    });
  return (
    <>
      <div className="section-heading">
        <div>
          <span className="eyebrow">WORKSPACE / DASHBOARDS</span>
          <h2>{current ? def.title : "Your dashboards"}</h2>
          <p>
            {current
              ? "A shared view of application activity."
              : "Turn event streams into a useful view of your system."}
          </p>
        </div>
        <div className="actions">
          {current && (
            <button
              onClick={() => {
                setCurrent(null);
                setEditing(false);
                setSelected(null);
              }}
            >
              All dashboards
            </button>
          )}
          {admin &&
            (current ? (
              <>
                <button onClick={() => setEditing(!editing)}>
                  {editing ? "Finish editing" : "Edit dashboard"}
                </button>
                <button
                  className="primary"
                  onClick={async () => {
                    try {
                      await api(
                        "/dashboards/" + current.dashboardId,
                        { ...def, timeRangeMinutes: minutes },
                        "PUT",
                      );
                      setEditing(false);
                      setError("");
                      void load();
                    } catch (e) {
                      setError(errorText(e));
                    }
                  }}
                >
                  Save dashboard
                </button>
              </>
            ) : (
              <button
                className="primary"
                onClick={() => {
                  setCurrent({
                    dashboardId: crypto.randomUUID(),
                    definition: {
                      title: "Application overview",
                      panels: starterPanels(),
                      refreshSeconds: 30,
                      timeRangeMinutes: minutes,
                    },
                  });
                  setEditing(true);
                }}
              >
                <Plus size={16} /> New dashboard
              </button>
            ))}
        </div>
      </div>
      <Notice error={error} />
      {!current ? (
        <div className="dashboard-list">
          {loading ? (
            <div className="empty">Loading dashboards…</div>
          ) : list.length ? (
            list.map((d) => (
              <button
                className="dashboard-card"
                key={d.dashboardId}
                onClick={() => {
                  setCurrent(d);
                  setMinutes(d.definition.timeRangeMinutes);
                }}
              >
                <div className="dashboard-icon">
                  <LayoutDashboard />
                </div>
                <h3>{d.definition.title}</h3>
                <p>
                  {d.definition.panels.length} panels · refresh every{" "}
                  {d.definition.refreshSeconds}s
                </p>
                <span>
                  Open dashboard <ArrowUpRight size={16} />
                </span>
              </button>
            ))
          ) : (
            <div className="card">
              <Empty
                text={
                  admin
                    ? "Create your first dashboard to connect the dots."
                    : "No dashboards have been created yet."
                }
              />
            </div>
          )}
        </div>
      ) : (
        <>
          {editing && (
            <div className="card editor-toolbar">
              <label>
                Dashboard title
                <input
                  value={def.title}
                  onChange={(e) => change({ title: e.target.value })}
                />
              </label>
              <label>
                Refresh interval
                <select
                  value={def.refreshSeconds}
                  onChange={(e) =>
                    change({ refreshSeconds: Number(e.target.value) })
                  }
                >
                  {[5, 10, 30, 60, 300].map((n) => (
                    <option key={n} value={n}>
                      {n} seconds
                    </option>
                  ))}
                </select>
              </label>
              <button
                onClick={() =>
                  change({
                    panels: [
                      ...def.panels,
                      {
                        i: crypto.randomUUID(),
                        title: "New panel",
                        type: "count",
                        x: 0,
                        y: 99,
                        w: 6,
                        h: 3,
                        query: {},
                      },
                    ],
                  })
                }
                disabled={def.panels.length >= 12}
              >
                <Plus size={16} /> Add panel
              </button>
              <button
                className="danger"
                onClick={async () => {
                  try {
                    await api(
                      "/dashboards/" + current.dashboardId,
                      undefined,
                      "DELETE",
                    );
                    setCurrent(null);
                    void load();
                  } catch (e) {
                    setError(errorText(e));
                  }
                }}
              >
                <Trash2 size={16} /> Delete
              </button>
            </div>
          )}
          <Grid
            className="dashboard-grid"
            cols={12}
            rowHeight={100}
            margin={[16, 16]}
            layout={def.panels.map((p: Obj) => ({
              i: p.i,
              x: p.x,
              y: p.y,
              w: p.w,
              h: p.h,
              minW: 3,
              minH: 2,
              maxH: 6,
            }))}
            isDraggable={editing}
            isResizable={editing}
            draggableHandle=".drag-handle"
            onLayoutChange={(layout: Layout[]) => {
              if (editing)
                change({
                  panels: def.panels.map((p: Obj) => ({
                    ...p,
                    ...layout.find((l) => l.i === p.i),
                  })),
                });
            }}
          >
            {def.panels.map((p: Obj) => (
              <div key={p.i} className="card panel">
                <div className="card-title">
                  <h3>{p.title}</h3>
                  {editing ? (
                    <div className="actions">
                      <button
                        className="tiny"
                        onClick={() => {
                          setSelected(p.i);
                          setQueryText(JSON.stringify(p.query, null, 2));
                        }}
                      >
                        Configure
                      </button>
                      <GripHorizontal size={18} className="drag-handle" />
                    </div>
                  ) : (
                    <span className="panel-dot" />
                  )}
                </div>
                <Panel p={p} minutes={minutes} tick={tick + localTick} />
              </div>
            ))}
          </Grid>
          {editing && selected && (
            <div className="card panel-editor">
              <h3>Panel configuration</h3>
              {def.panels
                .filter((p: Obj) => p.i === selected)
                .map((p: Obj) => (
                  <div key={p.i}>
                    <div className="filter-grid">
                      <label>
                        Title
                        <input
                          value={p.title}
                          onChange={(e) =>
                            updatePanel(p.i, { title: e.target.value })
                          }
                        />
                      </label>
                      <label>
                        Visualization
                        <select
                          value={p.type}
                          onChange={(e) =>
                            updatePanel(p.i, { type: e.target.value })
                          }
                        >
                          {[
                            "logs",
                            "timeseries",
                            "count",
                            "bar",
                            "sum",
                            "avg",
                          ].map((t) => (
                            <option key={t}>{t}</option>
                          ))}
                        </select>
                      </label>
                    </div>
                    <label>
                      Structured query
                      <textarea
                        value={queryText}
                        onChange={(e) => setQueryText(e.target.value)}
                      />
                    </label>
                    <p className="muted">
                      Example numeric query:{" "}
                      {
                        '{"eventType":"ORDER_CREATED","schemaVersion":1,"field":"payload.amount"}'
                      }
                    </p>
                    <div className="actions">
                      <button
                        className="primary"
                        onClick={() => {
                          try {
                            updatePanel(p.i, { query: JSON.parse(queryText) });
                            setError("");
                          } catch {
                            setError("Query must be valid JSON");
                          }
                        }}
                      >
                        Apply query
                      </button>
                      <button
                        className="danger"
                        onClick={() => {
                          change({
                            panels: def.panels.filter((v: Obj) => v.i !== p.i),
                          });
                          setSelected(null);
                        }}
                      >
                        Remove panel
                      </button>
                    </div>
                  </div>
                ))}
            </div>
          )}
        </>
      )}
    </>
  );
}
function Applications({ admin }: { admin: boolean }) {
  const [apps, setApps] = useState<Obj[]>([]),
    [keys, setKeys] = useState<Obj[]>([]),
    [selected, setSelected] = useState(""),
    [raw, setRaw] = useState(""),
    [name, setName] = useState(""),
    [id, setId] = useState(""),
    [error, setError] = useState("");
  const load = () =>
    api("/applications")
      .then(setApps)
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    if (selected)
      api("/applications/" + selected + "/keys")
        .then(setKeys)
        .catch((e) => setError(errorText(e)));
    setRaw("");
  }, [selected]);
  return (
    <>
      <div className="section-heading">
        <div>
          <h2>Applications</h2>
          <p>Connect services and manage their ingestion credentials.</p>
        </div>
        <span className="chip">
          <Boxes size={14} />
          {apps.length} connected
        </span>
      </div>
      <Notice error={error} />
      {admin && (
        <form
          className="card editor-toolbar"
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              await api("/applications", { applicationId: id, name });
              setName("");
              setId("");
              void load();
            } catch (e) {
              setError(errorText(e));
            }
          }}
        >
          <label>
            Application ID
            <input
              required
              pattern="[a-z0-9-]+"
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="payment-service"
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Payment Service"
            />
          </label>
          <button className="primary">
            <Plus size={16} /> Add application
          </button>
        </form>
      )}
      <div className="dashboard-list">
        {apps.map((a) => (
          <button
            className={
              "dashboard-card " +
              (selected === a.applicationId ? "selected" : "")
            }
            key={a.applicationId}
            onClick={() => setSelected(a.applicationId)}
          >
            <Boxes />
            <h3>{a.name}</h3>
            <p className="mono">{a.applicationId}</p>
            <span>
              Manage credentials <ArrowUpRight size={16} />
            </span>
          </button>
        ))}
      </div>
      {selected && (
        <div className="card credentials">
          <div className="ingestion-status">
            <h3>Ingestion outcomes</h3>
            <p className="muted">
              Accepted:{" "}
              {apps.find((a) => a.applicationId === selected)?.ingestion
                ?.accepted || 0}{" "}
              · Rejected:{" "}
              {apps.find((a) => a.applicationId === selected)?.ingestion
                ?.rejected || 0}{" "}
              · Retryable:{" "}
              {apps.find((a) => a.applicationId === selected)?.ingestion
                ?.retryable || 0}
            </p>
            <Notice
              error={
                apps.find((a) => a.applicationId === selected)
                  ?.lastIngestionError
                  ? "Last ingestion issue: " +
                    apps.find((a) => a.applicationId === selected)
                      ?.lastIngestionError
                  : ""
              }
            />
            <small>
              Best-effort attempt counters; retries may be counted more than
              once. Reload this page for current totals.
            </small>
          </div>
          <div className="card-title">
            <h3>{selected} / API keys</h3>
            {admin && (
              <button
                className="primary"
                onClick={async () => {
                  try {
                    const d = await api(
                      "/applications/" + selected + "/keys",
                      {},
                      "POST",
                    );
                    setRaw(d.apiKey);
                    setKeys(await api("/applications/" + selected + "/keys"));
                  } catch (e) {
                    setError(errorText(e));
                  }
                }}
              >
                <Plus size={15} /> Create key
              </button>
            )}
          </div>
          {raw && (
            <div className="key-reveal">
              <b>Copy this key now. It will only be shown once.</b>
              <code>{raw}</code>
              <button onClick={() => setRaw("")}>Dismiss</button>
            </div>
          )}
          <table>
            <thead>
              <tr>
                <th>Credential</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k.keyId}>
                  <td className="mono">{k.keyId}</td>
                  <td>
                    <span className={"badge " + (k.revoked ? "warn" : "info")}>
                      {k.revoked ? "Revoked" : "Active"}
                    </span>
                  </td>
                  <td>
                    {admin && !k.revoked && (
                      <button
                        className="danger"
                        onClick={async () => {
                          try {
                            await api(
                              "/applications/" + selected + "/keys/" + k.keyId,
                              undefined,
                              "DELETE",
                            );
                            setKeys(
                              await api("/applications/" + selected + "/keys"),
                            );
                          } catch (e) {
                            setError(errorText(e));
                          }
                        }}
                      >
                        Revoke
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!keys.length && <Empty text="No API keys for this application." />}
        </div>
      )}
    </>
  );
}
const blankSchema = {
  $schema: "https://json-schema.org/draft/2020-12/schema",
  type: "object",
  additionalProperties: false,
  required: ["name"],
  properties: { name: { type: "string", "x-queryable": true } },
};
function Schemas({ admin }: { admin: boolean }) {
  const [list, setList] = useState<Obj[]>([]),
    [type, setType] = useState("CUSTOM_EVENT"),
    [version, setVersion] = useState(1),
    [schema, setSchema] = useState(JSON.stringify(blankSchema, null, 2)),
    [sample, setSample] = useState('{"name":"example"}'),
    [selected, setSelected] = useState<Obj | null>(null),
    [result, setResult] = useState(""),
    [error, setError] = useState("");
  const load = () =>
    api("/schemas")
      .then(setList)
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    void load();
  }, []);
  const act = async (fn: () => Promise<unknown>) => {
    try {
      await fn();
      setError("");
      void load();
    } catch (e) {
      setError(errorText(e));
    }
  };
  return (
    <>
      <div className="section-heading">
        <div>
          <h2>Event schemas</h2>
          <p>A stable envelope. A payload that fits your application.</p>
        </div>
        <span className="chip">JSON Schema · 2020-12</span>
      </div>
      <Notice error={error} />
      <div className="schema-layout">
        <div className="card schema-nav">
          <div className="card-title">
            <h3>Schema registry</h3>
            {admin && (
              <button
                className="tiny"
                aria-label="New schema"
                onClick={() => {
                  setSelected(null);
                  setType("CUSTOM_EVENT");
                  setVersion(1);
                  setSchema(JSON.stringify(blankSchema, null, 2));
                  setResult("");
                }}
              >
                <Plus size={16} />
              </button>
            )}
          </div>
          {list.map((s) => (
            <button
              className={
                selected?.eventType === s.eventType &&
                selected?.version === s.version
                  ? "active"
                  : ""
              }
              key={s.eventType + s.version}
              onClick={() => {
                setSelected(s);
                setType(s.eventType);
                setVersion(s.version);
                setSchema(JSON.stringify(JSON.parse(s.schema), null, 2));
                setResult("");
              }}
            >
              <Braces size={17} />
              <span>
                {s.eventType}
                <small>
                  Version {s.version} · {s.published ? "Published" : "Draft"}
                </small>
              </span>
            </button>
          ))}
        </div>
        <div className="card schema-editor">
          <div className="card-title">
            <h3>
              {selected?.published
                ? "Published version · read only"
                : selected
                  ? "Edit draft"
                  : "New schema draft"}
            </h3>
          </div>
          <div className="filter-grid">
            <label>
              Event type
              <input
                value={type}
                disabled={!!selected || !admin}
                onChange={(e) => setType(e.target.value)}
              />
            </label>
            <label>
              Version
              <input
                type="number"
                min={1}
                value={version}
                disabled={!!selected || !admin}
                onChange={(e) => setVersion(Number(e.target.value))}
              />
            </label>
          </div>
          <label>
            Payload schema
            <textarea
              className="code-editor"
              value={schema}
              readOnly={!admin || selected?.published}
              onChange={(e) => setSchema(e.target.value)}
            />
          </label>
          {admin && (
            <div className="actions">
              {!selected ? (
                <button
                  className="primary"
                  onClick={() =>
                    act(async () => {
                      const d = await api("/schemas", {
                        eventType: type,
                        version,
                        schema: JSON.parse(schema),
                      });
                      setSelected(d);
                    })
                  }
                >
                  Create draft
                </button>
              ) : selected.published ? (
                <button
                  onClick={() => {
                    setSelected(null);
                    setVersion(
                      Math.max(
                        ...list
                          .filter((s) => s.eventType === type)
                          .map((s) => s.version),
                      ) + 1,
                    );
                  }}
                >
                  Create next version
                </button>
              ) : (
                <>
                  <button
                    className="primary"
                    onClick={() =>
                      act(() =>
                        api(
                          `/schemas/${type}/${version}`,
                          JSON.parse(schema),
                          "PUT",
                        ),
                      )
                    }
                  >
                    Save draft
                  </button>
                  <button
                    onClick={() =>
                      act(async () => {
                        await api(`/schemas/${type}/${version}/publish`, {});
                        setSelected({ ...selected, published: true });
                      })
                    }
                  >
                    Publish immutable version
                  </button>
                </>
              )}
            </div>
          )}
          {selected && admin && (
            <>
              <hr />
              <label>
                Example payload
                <textarea
                  value={sample}
                  onChange={(e) => setSample(e.target.value)}
                />
              </label>
              <button
                onClick={() =>
                  act(async () => {
                    const d = await api(
                      `/schemas/${type}/${version}/validate`,
                      JSON.parse(sample),
                    );
                    setResult(
                      d.valid
                        ? "✓ Payload matches this schema"
                        : d.errors.join("\n"),
                    );
                  })
                }
              >
                Validate saved schema
              </button>
              {result && <pre>{result}</pre>}
            </>
          )}
        </div>
      </div>
    </>
  );
}
function App() {
  const [authenticated, setAuthenticated] = useState(!!token),
    [user, setUser] = useState<Obj | null>(null),
    [page, setPage] = useState("Dashboards"),
    [minutes, setMinutes] = useState(60),
    [tick, setTick] = useState(0),
    [error, setError] = useState("");
  useEffect(() => {
    const logout = () => {
      setAuthenticated(false);
      setUser(null);
    };
    window.addEventListener("auth-expired", logout);
    return () => window.removeEventListener("auth-expired", logout);
  }, []);
  useEffect(() => {
    if (authenticated)
      api("/auth/me")
        .then(setUser)
        .catch((e) => setError(errorText(e)));
  }, [authenticated]);
  if (!authenticated) return <Login done={() => setAuthenticated(true)} />;
  const admin = user?.role === "ADMIN";
  return (
    <div className="shell">
      <aside>
        <div className="brand">
          <Activity /> observatory<span>•</span>
        </div>
        <div className="workspace">
          <span className="workspace-icon">O</span>
          <div>
            Development
            <small>{user?.tenantId || "Connecting…"} workspace</small>
          </div>
          <ChevronRight size={14} />
        </div>
        <span className="nav-label">OBSERVE</span>
        <nav>
          {[
            [LayoutDashboard, "Dashboards"],
            [Search, "Explore"],
            [Boxes, "Applications"],
            [Braces, "Schemas"],
          ].map(([Icon, label]: any) => (
            <button
              key={label}
              className={page === label ? "active" : ""}
              onClick={() => setPage(label)}
            >
              <Icon size={18} />
              {label}
              {page === label && <span className="nav-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="pipeline">
            <Radio size={17} />
            <div>
              Event pipeline<small>Kafka → MongoDB</small>
            </div>
          </div>
          <div className="profile">
            <span className="avatar">{admin ? "A" : "V"}</span>
            <div>
              {admin ? "Administrator" : "Viewer"}
              <small>{user?.email}</small>
            </div>
            <button
              aria-label="Sign out"
              onClick={async () => {
                try {
                  await api("/auth/logout", {});
                } finally {
                  token = "";
                  sessionStorage.removeItem("observatory-token");
                  setAuthenticated(false);
                }
              }}
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      <div className="main">
        <header>
          <div className="breadcrumb">
            Workspace <ChevronRight size={12} />
            <b>{page}</b>
          </div>
          <div className="actions">
            <span className="role">
              <Shield size={13} />
              {user?.role}
            </span>
            <div className="time-select">
              <Clock size={14} />
              <select
                aria-label="Time range"
                value={minutes}
                onChange={(e) => setMinutes(Number(e.target.value))}
              >
                {[
                  [15, "Last 15 minutes"],
                  [60, "Last hour"],
                  [360, "Last 6 hours"],
                  [1440, "Last 24 hours"],
                  [10080, "Last 7 days"],
                  [43200, "Last 30 days"],
                ].map(([v, s]) => (
                  <option key={v} value={v}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            <button
              className="icon-button"
              aria-label="Refresh"
              onClick={() => setTick((t) => t + 1)}
            >
              <RefreshCw size={16} />
            </button>
          </div>
        </header>
        <main>
          <Notice error={error} />
          {page === "Dashboards" ? (
            <Dashboards
              admin={admin}
              minutes={minutes}
              tick={tick}
              setMinutes={setMinutes}
            />
          ) : page === "Explore" ? (
            <Explorer minutes={minutes} tick={tick} />
          ) : page === "Applications" ? (
            <Applications admin={admin} />
          ) : (
            <Schemas admin={admin} />
          )}
        </main>
        <footer>
          <span>
            OBSERVATORY{" "}
            <span className="muted">/ Event observability platform</span>
          </span>
          <span>At-least-once ingestion · server-scoped queries</span>
        </footer>
      </div>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
