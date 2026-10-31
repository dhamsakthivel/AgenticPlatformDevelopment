import type { Metadata } from "next";
import { Figtree, Fraunces } from "next/font/google";
import { Shell } from "@/components/Shell";
import "./globals.css";

const sans = Figtree({ subsets: ["latin"], variable: "--font-sans" });
const serif = Fraunces({ subsets: ["latin"], variable: "--font-serif" });

export const metadata: Metadata = {
  title: "AKE · User story agent",
  description: "Agentic Knowledge Engine for drafting user stories from a document library.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={`${sans.variable} ${serif.variable}`} style={{ fontFamily: "var(--font-sans), Figtree, sans-serif" }}>
        <style>{`:root { --sans: var(--font-sans), Figtree, sans-serif; --serif: var(--font-serif), Fraunces, serif; }`}</style>
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
