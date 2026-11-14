import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sheet } from "@/components/ui/overlay";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { ErrorBox, Notice } from "@/components/ui/feedback";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { api, download } from "@/lib/api";
import type { ImportResult } from "@/lib/types";

/** Bulk booking from a CSV file. Every row goes through the same screen, check and book pipeline. */
export function ImportSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [waitlist, setWaitlist] = useState(false);
  const upload = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append("file", file!);
      return api.post<ImportResult>(`/api/reservations/import?joinWaitlist=${waitlist}`, form);
    },
    onSuccess: () => queryClient.invalidateQueries(),
  });
  const r = upload.data;

  return (
    <Sheet
      open={open}
      onOpenChange={(o) => {
        onOpenChange(o);
        if (!o) {
          upload.reset();
          setFile(null);
        }
      }}
      wide
      title="Import reservations"
      description="Each row is screened, checked for availability and booked exactly like a booking made by hand."
      footer={
        <>
          <Button onClick={() => download("/api/reservations/import-template", "reservations-template.csv")}>Download template</Button>
          <Button variant="primary" disabled={!file} loading={upload.isPending} onClick={() => upload.mutate()}>
            Import
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <Field label="CSV file" hint="Columns: guestName, email, phone, roomType, checkIn, checkOut, rooms, adults, then optional needsAccessible, maxFloor, preferredFloor, acceptsUpgrade, notes. Up to 500 rows.">
          {(id) => <Input id={id} type="file" accept=".csv,text/csv" className="py-1.5" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />}
        </Field>
        <Checkbox label="Put rows that don't fit on the waitlist" hint="Otherwise they are reported as unavailable and not saved" checked={waitlist} onChange={setWaitlist} />
        <ErrorBox error={upload.error} />
        {r && (
          <>
            <Notice tone={r.failed || r.unavailable ? "warn" : "ok"} title={`${r.rows} rows read`}>
              {r.booked} booked, {r.waitlisted} waitlisted, {r.held} held for review, {r.unavailable} unavailable, {r.failed} with errors.
            </Notice>
            <TableWrap className="rounded-md border border-line">
              <Table>
                <thead>
                  <tr>
                    <Th>Line</Th>
                    <Th>Guest</Th>
                    <Th>Result</Th>
                    <Th>Details</Th>
                  </tr>
                </thead>
                <tbody>
                  {r.results.map((row) => (
                    <Tr key={row.line}>
                      <Td className="tabular">{row.line}</Td>
                      <Td>{row.guestName}</Td>
                      <Td>
                        <Badge tone={row.outcome === "BOOKED" ? "green" : row.outcome === "FAILED" ? "coral" : "amber"}>{row.outcome.toLowerCase()}</Badge>
                      </Td>
                      <Td className="whitespace-normal text-muted">{row.reference ? `${row.reference}. ` : ""}{row.message}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
          </>
        )}
      </div>
    </Sheet>
  );
}
