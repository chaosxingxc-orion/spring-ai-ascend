interface ScrollToBottomButtonProps {
  visible: boolean;
  onClick: () => void;
}

export function ScrollToBottomButton({ visible, onClick }: ScrollToBottomButtonProps) {
  if (!visible) {
    return null;
  }

  return (
    <button
      type="button"
      className="scroll-to-bottom-btn"
      aria-label="回到底部"
      title="回到底部"
      onClick={onClick}
    >
      ↓
    </button>
  );
}
