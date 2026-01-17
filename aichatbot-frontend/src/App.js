import logo from './logo.svg';
import './App.css';
import DocumentIngest from './components/DocumentIngest';
import NewApp from "./NewApp";

function App() {
  return (
    // <DocumentIngest />
      <NewApp/>
  );
}

export default App;



/*
I have designed and implemented AIChatbot application using SpringBooot for backedend. I want to implement frontend using ReactJS. The application should have the following features:
1. User Authentication: Implement user login and registration functionality.
2. Chat Interface: Create a chat interface where users can interact with the AI chatbot.
3. Model Selection: Allow users to select different AI models (e.g., GPT-3, GPT-4) for their conversations.
4. Conversation History: Store and display past conversations for users to review.
5. Document Ingestion: Enable users to upload documents that the AI can reference during chats.
6. Responsive Design: Ensure the application is mobile-friendly and works well on various screen sizes.
7. http://localhost:8080/api/v1/ is the base url for backend API integration,
8. It should also support streaming responses from the backend for real-time chat updates.
Please provide the complete ReactJS code for the frontend application, including all necessary components, state management, and API integration with the SpringBoot backend.

*/


